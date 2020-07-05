package house;


import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import proto.HouseProtoMessage.MeasurementInfoListProto;
import proto.HouseProtoMessage.MeasurementInfoProto;
import proto.HouseProtoMessage.HouseInfoListProto;
import proto.HouseProtoMessage.HouseInfoProto;
import proto.rpc.GrpcHouseProto.*;
import proto.rpc.HouseGrpc;
import proto.wrapper.EmptyStreamObserver;
import proto.wrapper.MeasurementBuffer;
import proto.wrapper.MessageHelper;
import proto.wrapper.MessageHelper.ControlValue;
import proto.wrapper.MessageHelper.TypeValue;
import provided.Measurement;
import provided.SmartMeterSimulator;
import util.Constant;
import util.GenericWrapper;
import util.exception.IdAlreadyUseException;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URISyntaxException;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;


public class House {

    private final static Logger LOGGER = Logger.getLogger(House.class.getName());

    private final Server server;

    private final MeasurementBuffer measurementBuffer;

    private HouseInfoProto myHouseData;

    private WebTarget serverAdministrator;

    private final HouseSynchro synchro = new HouseSynchro();

    private final CommunicationChannel predecessor = new CommunicationChannel();

    private final CommunicationChannel successor = new CommunicationChannel();

    private MessageHelper msgHelper;

    private final SmartMeterSimulator simulator;

    private final GenericWrapper<Boolean> imCoordinator = new GenericWrapper<>(false);

    /**
     * Construct the HouseNode:
     * > set up it's parameter
     * > start it's simulator
     * > greet the server and receiving the list of the other Houses or fail (another house with the same ID already exist)
     * > greet all the other houses.
     *
     * If everything works well the House is in the peer-to-peer network.
     * @param id the ID
     * @param port that this house use for communication with the other Houses
     * @param serverHost host:port
     * @throws URISyntaxException in case the serverHost isn't in the form host:port
     * @throws IdAlreadyUseException in case exist in the net another house with my id
     * @throws IOException the server-side can throw this error
     */
    public House(String id, int port, String serverHost) throws URISyntaxException, IdAlreadyUseException, IOException{
        //LOGGER.info("Start Initializing");
        server = ServerBuilder.forPort(port).addService(new HouseServiceP2P(this)).build();
        this.myHouseData =
                HouseInfoProto.newBuilder()
                        .setId(id)
                        .setPort(port)
                        .setIp("localhost")
                        .build();
        msgHelper = new MessageHelper(id);
        SlidingWindowBuffer sensorMeasurement = new SlidingWindowBuffer(Constant.MEASUREMENT_BUFFER_CAPACITY, Constant.MEASUREMENT_SLIDING_WINDOW);
        this.simulator = new SmartMeterSimulator(id, sensorMeasurement);
        this.measurementBuffer = new MeasurementBuffer();
        //LOGGER.info("Contact Server at: "+ serverHost);
        //Server communication
        Client client = ClientBuilder.newBuilder().build();
        this.serverAdministrator = client.target(serverHost).path(Constant.Path.HOUSES);
        System.out.println("\n\n\n\nHi my name is:"+id);
        helloProcedure();

        server.start();

        //Watcher Thread, will send the my local measurement to the successor or directly to the server if successor is down
        new Thread(() -> {
            Measurement m = null;
            while(true) {
                m = sensorMeasurement.getMeasurement();
                MeasurementInfoProto measurementInfoProto = MeasurementInfoProto.newBuilder()
                        .setTimestamp(m.getTimestamp())
                        .setId(m.getId())
                        .setValue(m.getValue())
                        .build();
                if (successor.isDown() && predecessor.isDown()) {
                    //I'm the only one in the network
                    //send the message directly on the server
                    measurementBuffer.addMeasure(measurementInfoProto);
                    MeasurementInfoListProto listProto = measurementBuffer.getCondomConsumption(true);
                    //LOGGER.info("\n Global Mean:" + listProto.getMeasurementInfo(0).getValue() + "\n");
                    Response r = serverAdministrator.path(Constant.Path.UPDATE)
                            .request(MediaType.APPLICATION_OCTET_STREAM)
                            .put(Entity.entity(listProto.toByteArray(), MediaType.APPLICATION_OCTET_STREAM));
                    if (r.getStatus() != Response.Status.OK.getStatusCode()) {
                        //LOGGER.warning("Something went wrong communicating the measurements to the server");
                    }
                } else {
                    MeasurementInfoMessageProto measurementInfoMessageProto = null;
                    boolean fakeMeasure = false;
                    //If I have to synchronize with the network
                    if (synchro.isFirstMessage()) {
                        //If I don't actually have the token, but I have generate a measure but I need to synchronized it
                        //I need to wait for the token before proceed for avoiding having my measures randomly in the network
                        synchro.waitMeanToken();
                        //I create a special packet, I will send my measure with the token and control Synchro
                        //If it's not the one who send to server, will add a fake measurement with ts-1 and my real measurement
                        //If it's the sender, will remove the Synchro control and forward the message
                        measurementInfoMessageProto = MeasurementInfoMessageProto.newBuilder()
                                .setMessage(MessageProto.newBuilder(msgHelper.getGenericMessage())
                                        .setType(TypeValue.MEAN.ordinal())
                                        .setControl(ControlValue.SYNCHRO.ordinal())
                                        .setMeanToken(synchro.getMeanToken().getObj())
                                        .build())
                                .setMeasurementInfo(measurementInfoProto)
                                .build();
                        synchro.setSynchroNeed(false);
                        fakeMeasure = imCoordinator.getObj();
                    } else {
                        measurementInfoMessageProto = MeasurementInfoMessageProto.newBuilder()
                                .setMessage(MessageProto.newBuilder(msgHelper.getTypedMessage(TypeValue.MEAN))
                                        .setMeanToken(measurementBuffer.atLeastTwoConfirmed() ? synchro.consumeToken() : "")
                                        .addAllBoostToken(BoostManager.getInstance().getAllTokens())
                                        .build())
                                .setMeasurementInfo(measurementInfoProto)
                                .build();
                    }
                    synchronized (successor) {
                        if (fakeMeasure) {
                            measurementBuffer.addMeasure(MeasurementBuffer.getFakeMeasure(myHouseData.getId()));
                        }
                        measurementBuffer.addPendingMeasure(measurementInfoProto);
                        successor.getAsynchStub().updateMeasurement(measurementInfoMessageProto, new EmptyStreamObserver());
                    }
                }
            }
        }).start();

        //create a watcher thread for
        new Thread(() -> {
            String token = null;
            while (true) {
                LOGGER.info("Wait for Boost Token");
                //Chiamata Bloccante finchè non viene aggiunto una prenotazione di boost e arrivi un boost token non usato
                token = BoostManager.getInstance().getToken();
                LOGGER.info("Boost Token: "+token +"\nStart Boost");
                //Boosto!
                try {
                    simulator.boost();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
                LOGGER.info("Finish Boosting");
                //If I'm solo I will put the token back like it wasn't ever used
                BoostManager.getInstance().addToken(token, !(successor.isDown() && predecessor.isDown()));
                BoostManager.getInstance().removeReservation();

            }
        }).start();

        simulator.start();
        //LOGGER.info("\nSUCCESSOR:" + successor +"\nPREDECESSOR:"+ predecessor);
        //LOGGER.info("Finish Initializing");
    }


    public static void main(String ... args) {
        //args[0] = id
        //args[1] = port
        try {
            House me = new House(args[0], Integer.valueOf(args[1]), Constant.DEFAULT_SERVER_URI.toString());

            System.out.println("\n\nFor a list of command digit \"help\"\n");

            BufferedReader inFromUser =
                    new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                String command = inFromUser.readLine();

                String[] commandSlice = command.split(" ");

                switch (commandSlice[0]) {
                    case "help":
                        System.out.println("List of Commands");
                        System.out.println("quit -> esc from the Network");
                        System.out.println("boost -> request boost from condominium, ");
                        break;
                    case "quit":
                        me.quitProcedure();
                        break;
                    case "boost":
                        BoostManager.getInstance().addReservation();
                        break;
                    default:
                        System.out.println("[ERROR]: command not exist, digit \"help\" for list of commands");
                }
            }
        }catch (URISyntaxException | IdAlreadyUseException | IOException e) {
            e.printStackTrace();
        }
    }


    private void helloProcedure() throws IdAlreadyUseException {
        //Contact Server
        Response response = this.serverAdministrator.path(Constant.Path.ADD)
                .request(MediaType.APPLICATION_OCTET_STREAM).post(Entity.entity(this.myHouseData.toByteArray(), MediaType.APPLICATION_OCTET_STREAM));

        //IF the response from server is Conflict throw exception
        if(response.getStatus() == Response.Status.CONFLICT.getStatusCode()) {
            //LOGGER.log(Level.SEVERE, "Id already used, shutdown");
            throw new IdAlreadyUseException(myHouseData.getId());
        } else if(response.getStatus() != Response.Status.OK.getStatusCode()) {
            //TODO gestire il caso in cui il server rifiuta il client per motivi interni suoi
            //LOGGER.log(Level.SEVERE, "Server Error, unhandled error");
        }
        //LOGGER.info("Response from server OK");
        //Assume the response is positive retrieve the list filtering out myself
        List<HouseInfoProto> houseList = new ArrayList<>();
        try {
            houseList = new ArrayList<>(HouseInfoListProto.parseFrom(response.readEntity(InputStream.class)).getHouseList()) ;
            houseList.remove(myHouseData);
        } catch (IOException e) {
            e.printStackTrace();
        }

        boolean solo = houseList.isEmpty();
        //If the list isn't empty I'm not solo
        if (!solo) {
            //Pick a random house and try to enter
            HouseMeasurementInfoMessageProto resp = null;
            //Build my greet message
            HouseInfoMessageProto message = HouseInfoMessageProto.newBuilder()
                    .setMessage(msgHelper.getGenericMessage())
                    .setHouseInfo(myHouseData)
                    .build();
            int i = 0;
            do {
                //HouseInfoProto targetHouse = new RandomPicker<HouseInfoProto>().remove(houseList);
                HouseInfoProto targetHouse = houseList.remove(0);
                this.successor.setChannel(targetHouse);
                LOGGER.info("Contact House at: " + targetHouse.getIp() + ":" + targetHouse.getPort());
                while (i < 3 && resp == null) {
                    try {
                        resp = this.successor.getBlockingStub().updatePredecessor(message);
                    } catch (io.grpc.StatusRuntimeException e) {
                        try {
                            LOGGER.info("Server House not Responding: "+i+" try");
                            Thread.sleep((long) (1000 * Math.pow(2,i++)));
                        } catch (InterruptedException ex) {
                            ex.printStackTrace();
                        }
                    }
                }
                i=0;
            } while (resp==null || !houseList.isEmpty() && resp.getMessage().getControl() != ControlValue.ACK.ordinal());

            if (resp.getMessage().getControl() == ControlValue.ACK.ordinal()) {
                msgHelper.update(resp.getMessage());
                //SET the token: this is jut an info about the token, I don't own the token for real, and it's reserved to condominium
                //So that the first time I see a token I can skip some control
                LOGGER.info("List of Measure: " + resp.getMeasurementList());
                this.measurementBuffer.initialize(resp.getMeasurementList(), resp.getLastConsumption());
                //LOGGER.info("Predecessor is: " + resp.getHouseInfo().getIp() + ":" +resp.getHouseInfo().getPort());
                this.predecessor.setChannel(resp.getHouseInfo());
            } else {
                //the message isn't ACK and it esc from do while so the list must be empty, I'm the only one in the network
                solo = true;
                this.successor.reset();
            }
        }
        if (solo) {
            synchro.setMeanToken(this.myHouseData.getId());
            BoostManager.getInstance().generateTokens();
            imCoordinator.setObj(true);
        }
    }


    private void quitProcedure() {
        LOGGER.info("Start Quitting");
        Response response = this.serverAdministrator.path(Constant.Path.DELETE+"/"+myHouseData.getId())
                .request(MediaType.APPLICATION_OCTET_STREAM).delete();

        LOGGER.info("Server Informed of Quitting : " + response.getStatus());
        //Assuming always a OK response

        //If I'm solo, just esc...
        if (successor.isDown()) {
            System.exit(0);
        }

        final GenericWrapper<Integer> ack = new GenericWrapper<>(0);

        MessageProto quitMessage = msgHelper.getTypedMessage(TypeValue.QUIT);


        synchro.getTopologyQueue().add(quitMessage);

        synchronized (quitMessage) {
            while (!synchro.getTopologyQueue().element().equals(quitMessage)) {
                LOGGER.info("Wait for resolve of message: " + synchro.getTopologyQueue().element());
                try {
                    quitMessage.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        MessageProto succMessage = MessageProto.newBuilder(quitMessage).setControl(ControlValue.UPDATE_PREDECESSOR.ordinal()).build();
        MessageProto predMessage = MessageProto.newBuilder(quitMessage).setControl(ControlValue.UPDATE_SUCCESSOR.ordinal()).build();

        final QuitStreamObserver toSucc = new QuitStreamObserver(successor, HouseInfoMessageProto.newBuilder().setMessage(succMessage).build(), ack);
        final QuitStreamObserver toPred = new QuitStreamObserver(predecessor, HouseInfoMessageProto.newBuilder().setMessage(predMessage).build(), ack);

        toSucc.sendFirstMessage();
        toPred.sendFirstMessage();

        //Wait for 2 ACK response
        synchronized (ack) {
            while (ack.getObj() < 2) {
                try {
                    ack.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            ack.setObj(0);
        }
        //Stop generating mean! when the QUIT message has priority
        this.simulator.stopMeGently();

        //If the pending messsage queue isn't empty wait until it becomes empty
        if (!measurementBuffer.getPendingMsg().isEmpty()) {
            synchronized (measurementBuffer.getPendingMsg()) {
                while(!measurementBuffer.getPendingMsg().isEmpty()) {
                    try {
                        measurementBuffer.getPendingMsg().wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        //Now I don't have any pending msg

        BoostManager.getInstance().quitProcedure();
        //Boost token are ok

        //If I wait for the token If i dont already have it avoiding exit while receiving it
        synchronized (synchro.getMeanToken()) {
            if (!synchro.getMeanToken().isPresent()) {
                try {
                    synchro.getMeanToken().wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            //Both pred and succ are informed of my quitting, send the update info and resolve quitting
            //I'll send the used Token to my successor, If I'm the coordinator I send the mean token
            MessageProto infoHeader = msgHelper.getGenericMessage();
            toSucc.completeQuit(HouseInfoMessageProto.newBuilder()
                    .setMessage(MessageProto.newBuilder(infoHeader)
                            .setControl(imCoordinator.getObj() ? ControlValue.CROWN.ordinal() : infoHeader.getControl())
                            .setMeanToken(imCoordinator.getObj() ? synchro.getMeanToken().getObj() : infoHeader.getMeanToken())
                            .addAllBoostToken(BoostManager.getInstance().getAllTokens())
                            .build())
                    .setHouseInfo(predecessor.getInfo())
                    .build());
            toPred.completeQuit(HouseInfoMessageProto.newBuilder()
                    .setMessage(MessageProto.newBuilder(infoHeader)
                            .setMeanToken(imCoordinator.getObj() ? infoHeader.getMeanToken() : synchro.getMeanToken().getObj())
                            .build())
                    .setHouseInfo(successor.getInfo())
                    .build());
        }



        synchronized (ack) {
            while (ack.getObj() < 2) {
                try {
                    ack.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        try {
            server.awaitTermination(3, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            server.shutdown();
        }
        synchro.resolveTopology();
        //LOGGER.info("Finish Quitting");
        System.exit(0);
    }


    private static class HouseServiceP2P extends HouseGrpc.HouseImplBase{

        private House house;

        public HouseServiceP2P(House inst) {
            this.house = inst;
        }


        @Override
        public void updatePredecessor(HouseInfoMessageProto request, StreamObserver<HouseMeasurementInfoMessageProto> responseObserver) {
            //this request is always the first message of an entering house so the timestamp will be considered with our clock
            //also I wont update the clock based on this message
            //Synchronized Block for the following case:
            //2 house A and B call updatePredecessor, A goes first and take ts=1, B start executing and take ts=2,
            //B keeps going and put it's message on the queue and see that it's message is on the head
            //A do the same and see it's message on the head (because ts=1 < ts=2).
            final HouseInfoMessageProto uRequest;
            boolean negativeResponse = false;
            //LOGGER.info("Receive Request");
            synchronized (this.house.synchro.getTopologyQueue()) {
                uRequest = HouseInfoMessageProto.newBuilder(request)
                        .setMessage(this.house.msgHelper.reSetTimer(request.getMessage()))
                        .build();
                if ( house.synchro.isQuitting(house.myHouseData.getId())) {
                    negativeResponse = true;
                } else {
                    this.house.synchro.getTopologyQueue().add(uRequest.getMessage());
                }
            }

            if(negativeResponse) {
                responseObserver.onNext(HouseMeasurementInfoMessageProto.newBuilder()
                        .setMessage(house.msgHelper.getControlMessage(ControlValue.NACK))
                        .build());
                responseObserver.onCompleted();
                return;
            }

            //until the head of the queue is my message I wont go on
            while(!this.house.synchro.getTopologyQueue().element().equals(uRequest.getMessage())) {
                //LOGGER.info("Wait for resolve of message: " + this.house.synchro.getTopologyQueue().peek());
                try {
                    synchronized (uRequest.getMessage()) {
                        uRequest.getMessage().wait();
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            //SPECIAL CASE: I'm the only one in the network ==> my predecessor and successor are null, finish the protocol with my information
            if (this.house.predecessor.isDown() && this.house.successor.isDown()) {
                //LOGGER.info("Special Case: we are only 2 in the network. Update predecessor and successor and Resolve");
                this.house.predecessor.setChannel(uRequest.getHouseInfo());
                this.house.successor.setChannel(uRequest.getHouseInfo());
                responseObserver.onNext(HouseMeasurementInfoMessageProto.newBuilder()
                        .setMessage(this.house.msgHelper.getGenericMessage())
                        .setHouseInfo(this.house.myHouseData)
                        .addAllMeasurement(house.measurementBuffer.getFullBuffer())
                        .build());
                this.house.synchro.resolveTopology();
                responseObserver.onCompleted();
                //LOGGER.info("\nSUCCESSOR:" + house.successor +"\nPREDECESSOR:"+ house.predecessor);
                return;
            }
            //this will be used in case the predecessor change due priority and cross messaging, when his quit message cross with update
            final HouseInfoProto snapPredecessor = this.house.predecessor.getInfo();

            //LOGGER.info("Contact predecessor for update");
            MeasurementInfoListMessageProto response = this.house.predecessor.getBlockingStub().updateSuccessor(request);
            house.msgHelper.update(response.getMessage());
            if (response.getMessage().getControl() == ControlValue.UPDATE_PREDECESSOR.ordinal()) {
                synchronized (house.predecessor) {
                    if(house.predecessor.equals(snapPredecessor)) {
                        //LOGGER.info("Predecessor is quitting, his info are :" + house.predecessor.getInfo());
                        try {
                            house.predecessor.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                    //LOGGER.info("Re-send request to new predecessor:" + house.predecessor.getInfo());
                    response = house.predecessor.getBlockingStub().updateSuccessor(request);
                }
            }

            //Avoid "Double Spending"
            synchronized (house.successor) {
                if(house.measurementBuffer.getLastConsumption() != response.getLastConsumption() && !house.imCoordinator.getObj()){
                    response = MeasurementInfoListMessageProto.newBuilder(response)
                            .addAllMeasurement(house.measurementBuffer.getMinBuffer())
                            .build();
                } else if (house.measurementBuffer.getLastConsumption() == response.getLastConsumption() && house.imCoordinator.getObj()) {
                    response = MeasurementInfoListMessageProto.newBuilder(response)
                            .addAllMeasurement(house.measurementBuffer.getLastMeasureBuffer())
                            .build();
                }
            }

            responseObserver.onNext(HouseMeasurementInfoMessageProto.newBuilder()
                    .setMessage(response.getMessage())
                    .addAllMeasurement(response.getMeasurementList())
                    .setLastConsumption(response.getLastConsumption())
                    .setHouseInfo(house.predecessor.getInfo())
                    .build());
            house.predecessor.setChannel(uRequest.getHouseInfo());
            house.synchro.resolveTopology();
            //LOGGER.info("\nSUCCESSOR:" + house.successor +"\nPREDECESSOR:"+ house.predecessor);
            responseObserver.onCompleted();
        }

        @Override
        public void updateSuccessor(HouseInfoMessageProto request, StreamObserver<MeasurementInfoListMessageProto> responseObserver) {
            //LOGGER.info("Receive Request");
            //No problem if this clock update isn't in a synchronized block. This method can't be called twice in the same moment
            //this.house.msgHelper.update(request.getMessage());
            PriorityQueue<MessageProto> topology = this.house.synchro.getTopologyQueue();
            boolean negativeResponse = false;
            synchronized (this.house.synchro.getTopologyQueue()) {
                //Check if the queue isn't empty and this message has priority on some other message in the queue.
                //e.g. : I was already Quitting and send the message quit to my successor while he call me this method.
                //on both sides we compare the messages, one of us will respond NACK and the other should go on.
                if (house.synchro.isQuitting(house.myHouseData.getId())
                && topology.comparator().compare(topology.peek(), request.getMessage()) < 0) {
                    LOGGER.info("Respond NACK\nreceived request:" + request.getMessage() + "\n" + topology.peek());
                    negativeResponse = true;
                }
            }
            if(negativeResponse) {
                responseObserver.onNext(MeasurementInfoListMessageProto.newBuilder()
                        .setMessage(house.msgHelper.getControlMessage(ControlValue.NACK))
                        .build());
                responseObserver.onCompleted();
                return;
            }
            synchronized (this.house.successor) {
            responseObserver.onNext(MeasurementInfoListMessageProto.newBuilder()
                    .setMessage(house.msgHelper.getControlMessage(ControlValue.ACK))
                    .addAllMeasurement(house.measurementBuffer.getFullBuffer())
                    .setLastConsumption(house.measurementBuffer.getLastConsumption())
                    .build());
                this.house.successor.setChannel(request.getHouseInfo());
                this.house.successor.notifyAll();
            }
            //LOGGER.info("\nSUCCESSOR:" + house.successor +"\nPREDECESSOR:"+ house.predecessor);
            responseObserver.onCompleted();
        }

        @Override
        public StreamObserver<HouseInfoMessageProto> bye(StreamObserver<MessageProto> responseObserver) {
            //LOGGER.info("Receive Request");
            return new StreamObserver<HouseInfoMessageProto>() {
                private CommunicationChannel target;
                private HouseInfoProto newTargetInfo;
                private boolean newCoordinator = false;
                private List<String> boostToken = new ArrayList<>();
                private String meanToken;

                @Override
                public void onNext(HouseInfoMessageProto houseInfoMessageProto) {
                    //This stream will accept 2 kind of messages
                    // 1. the quit packet from the client, which will be stored if it has priority
                    // 2. the info of my new succ/pred
                    house.msgHelper.update(houseInfoMessageProto.getMessage());
                    PriorityQueue<MessageProto> topology = house.synchro.getTopologyQueue();
                    if (houseInfoMessageProto.getMessage().getControl() == ControlValue.UPDATE_PREDECESSOR.ordinal()
                    || houseInfoMessageProto.getMessage().getControl() == ControlValue.UPDATE_SUCCESSOR.ordinal()) {
                        //(1)
                        //LOGGER.info("Receive QUIT message:"+houseInfoMessageProto);
                        if (houseInfoMessageProto.getMessage().getControl() == ControlValue.UPDATE_PREDECESSOR.ordinal()) {
                            target = house.predecessor;
                        } else {
                            target = house.successor;
                        }
                        boolean negativeResponse = false;
                        synchronized (house.synchro.getTopologyQueue()) {
                            //this means, negative response if my predecessor want to quit when a node is entering on me and he has the priority
                            // OR if I'm already quitting and I have priority over the request
                            if (topology.peek() != null
                                    && (topology.peek().getType() == TypeValue.ENTER.ordinal()
                                    && houseInfoMessageProto.getMessage().getControl() == ControlValue.UPDATE_PREDECESSOR.ordinal()
                                    || topology.peek().getType() == TypeValue.QUIT.ordinal()
                                    && topology.peek().getId().equals(house.myHouseData.getId()))
                                    && topology.comparator().compare(topology.peek(), houseInfoMessageProto.getMessage()) < 0) {
                                //LOGGER.info("Respond NACK\nreceived request:" + houseInfoMessageProto.getMessage() + "\n" + topology.peek());
                                negativeResponse = true;
                            } else {
                                //Add the message to the queue so that I delay other request until the node quit
                                topology.add(houseInfoMessageProto.getMessage());
                            }
                        }
                        if(negativeResponse) {
                            responseObserver.onNext(house.msgHelper.getControlMessage(ControlValue.NACK));
                            responseObserver.onCompleted();
                            return;
                        } else {
                            responseObserver.onNext(house.msgHelper.getControlMessage(ControlValue.ACK));
                        }
                    } else {
                        //(2)
                        //I won't check the priority on this message, I assume everybody respect the protocol
                        //The only thing I need to do is save the info of the new target wait for completed call
                        newTargetInfo = houseInfoMessageProto.getHouseInfo();
                        if (houseInfoMessageProto.getMessage().getControl() == ControlValue.CROWN.ordinal()) {
                            newCoordinator = true;
                        }
                        if (!houseInfoMessageProto.getMessage().getMeanToken().isEmpty()) {
                            meanToken = houseInfoMessageProto.getMessage().getMeanToken();
                        }
                        if (houseInfoMessageProto.getMessage().getBoostTokenCount() > 0) {
                            boostToken = houseInfoMessageProto.getMessage().getBoostTokenList();
                        }
                    }
                }

                @Override
                public void onError(Throwable throwable) {

                }

                @Override
                public void onCompleted() {
                    if(target == null || newTargetInfo == null) {
                        //LOGGER.info("Can't Complete the QUIT request: " + target + newTargetInfo);
                        return;
                    }
                    synchronized (target) {
                        //If the one who quit send me myself data I'm solo in the Network
                        if (newTargetInfo.equals(house.myHouseData)) {
                            target.reset();
                            house.synchro.setSynchroNeed(true);
                        } else {
                            target.setChannel(newTargetInfo);
                        }
                        target.notifyAll();
                    }
                    if (newCoordinator) {
                        house.imCoordinator.setObj(true);
                        house.synchro.setMeanToken(house.myHouseData.getId());
                        MeasurementInfoListProto listProto = house.measurementBuffer.getCondomConsumption(false);
                        //LOGGER.info("\nGlobal Mean:" + listProto.getMeasurementInfo(0).getValue() + "\n");
                    } else {
                        //Set the token wait to forward it
                        if (meanToken != null) {
                            house.synchro.setMeanToken(meanToken);
                        }
                    }
                    boostToken.forEach(x -> BoostManager.getInstance().addToken(x,false));
                    responseObserver.onCompleted();
                    house.synchro.resolveTopology();
                    //LOGGER.info("\nSUCCESSOR:" + house.successor +"\nPREDECESSOR:"+ house.predecessor);
                }
            };
        }


        @Override
        public void updateMeasurement(MeasurementInfoMessageProto measurement, StreamObserver<MessageProto> responseObserver) {
            //LOGGER.info("Receive Request:\n" + measurement.getMessage()+"\n"+measurement.getMeasurementInfo());
            MeasurementInfoListProto listProto = null;
            boolean condomCons = false;
            boolean withMeasure = false;
            boolean fakeMeas = false;
            boolean myfake = false;
            house.msgHelper.update(measurement.getMessage());
            //If the message is from me I remove from the pending message
//            if (measurement.getMessage().getId().equals(house.myHouseData.getId())) {
//                house.measurementBuffer.removePendingMeasure(measurement.getMeasurementInfo());
////            } else {
////                house.measurementBuffer.addMeasure(measurement.getMeasurementInfo());
//            }

            //If there's the MEAN token
            if (!measurement.getMessage().getMeanToken().isEmpty()) {
                if (measurement.getMessage().getControl() == ControlValue.SYNCHRO.ordinal()) {
                    //If It has my ID I will remove the Synchro Token before forward the message otherwise I will add a fake measurement
                    if (measurement.getMessage().getMeanToken().equals(house.myHouseData.getId())) {
                        measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                                .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                        .setMeanToken("")
                                        .setControl(ControlValue.ACK.ordinal())
                                        .build())
                                .build();
                        if (measurement.getMessage().getId().equals(house.myHouseData.getId())) {
                            //Should be 0
                            condomCons = true;
                            //listProto = house.measurementBuffer.getCondomConsumption(false);
                        }
                    } else {
                        fakeMeas = true;
                        //Add the mean to the queue but with a "padding" a fake measure that won't show up
//                        MeasurementInfoProto fakeMeasure = MeasurementInfoProto.newBuilder(measurement.getMeasurementInfo())
//                                .setTimestamp(measurement.getMeasurementInfo().getTimestamp()-1)
//                                .setValue(MeasurementBuffer.EXCLUDED_VALUE)
//                                .build();
//                        house.measurementBuffer.addMeasure(fakeMeasure);
                    }
                } else {
                    //If it has my ID I'll send the consumption to the server, otherwise I'll just calculate the consumption and print it
                    if (measurement.getMessage().getMeanToken().equals(house.myHouseData.getId())) {
                        withMeasure = true;
                        //listProto = house.measurementBuffer.getCondomConsumption(true);
                        //LOGGER.info("Sending Global Consumption:" + listProto.getMeasurementInfo(0));
//                        Response r = house.serverAdministrator.path(Constant.Path.UPDATE)
//                                .request(MediaType.APPLICATION_OCTET_STREAM)
//                                .put(Entity.entity(listProto.toByteArray(), MediaType.APPLICATION_OCTET_STREAM));
//                        if (r.getStatus() != Response.Status.OK.getStatusCode()) {
//                            //LOGGER.warning("Something went wrong communicating the measurements to the server");
//                        }
                    }

                    condomCons = true;
                    
//                    if (house.synchro.isFirstMessage() && !house.measurementBuffer.checkLastCons()) {
//                        listProto = MeasurementInfoListProto.newBuilder()
//                                .addMeasurementInfo(MeasurementInfoProto.newBuilder()
//                                        .setId(Constant.CONDOM_ID)
//                                        .setValue(house.measurementBuffer.getLastConsumption())
//                                        .build())
//                                .build();
//                    } else {
//                        condomCons = true;
//                    }

                    //If I don't have any confirmed measure OR the message was sent from me, I'll take the token for me and remove it
                    if (!house.measurementBuffer.atLeastTwoConfirmed()
                            || measurement.getMessage().getId().equals(house.myHouseData.getId())) {
                        house.synchro.setMeanToken(measurement.getMessage().getMeanToken());
                        measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                                .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                        .setMeanToken("")
                                        .build())
                                .build();
                    }
                }
            } else {
                //There isn't the token
                //If the message isn't from me And i have the token and:
                //I'm quitting and I'm not the coordinator OR I have at least one measure ready and I'm not quitting
                if (!measurement.getMessage().getId().equals(house.myHouseData.getId())
                && house.synchro.getMeanToken().isPresent()
                && (house.synchro.isQuitting(house.myHouseData.getId()) && !house.imCoordinator.getObj()
                    || house.measurementBuffer.atLeastTwoConfirmed()
                    && !house.synchro.isQuitting(house.myHouseData.getId()))) {
                    measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                            .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                    .setMeanToken(house.synchro.consumeToken())
                                    .build())
                            .build();
                }
            }


            //If there are some tokens I take them all if the message is from me, If it's from someone else I take just one token
            //This can help forward the token faster
            if (measurement.getMessage().getBoostTokenCount() > 0) {
                if (measurement.getMessage().getId().equals(house.myHouseData.getId())) {
                    List<String> boostTokens = new ArrayList<>(measurement.getMessage().getBoostTokenList());
                    boostTokens.forEach(x -> BoostManager.getInstance().addToken(x,false));
                    measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                            .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                    .clearBoostToken()
                                    .build())
                            .build();
                } else {
                    List<String> boostTokens = new ArrayList<>(measurement.getMessage().getBoostTokenList());
                    BoostManager.getInstance().addToken(boostTokens.remove(0), false);
                    measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                            .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                    .clearBoostToken()
                                    .addAllBoostToken(boostTokens)
                                    .build())
                            .build();
                }
            } else {
                //If there isn't any token on board I will upload mine (if I have it/them)
                //If the message isn't from me! (cause I will forward it)
                if (!measurement.getMessage().getId().equals(house.myHouseData.getId())) {
                    measurement = MeasurementInfoMessageProto.newBuilder(measurement)
                            .setMessage(MessageProto.newBuilder(measurement.getMessage())
                                    .addAllBoostToken(BoostManager.getInstance().getAllTokens())
                                    .build())
                            .build();
                }
            }

            synchronized (house.successor) {
                if (fakeMeas) {
                    house.measurementBuffer.addMeasure(MeasurementBuffer.getFakeMeasure(measurement.getMeasurementInfo().getId()));
                }
                //If the message wasn't sent from me I store the measure and forward it
                if (!measurement.getMessage().getId().equals(house.myHouseData.getId())) {
                    house.measurementBuffer.addMeasure(measurement.getMeasurementInfo());
                    house.successor.getAsynchStub().updateMeasurement(measurement, new EmptyStreamObserver());
                } else {
                    house.measurementBuffer.removePendingMeasure(measurement.getMeasurementInfo());
                }

                if (condomCons) {
                    listProto = house.measurementBuffer.getCondomConsumption(withMeasure);
                }
            }


            if (condomCons && withMeasure) {
                //LOGGER.info("Sending Global Consumption:" + listProto.getMeasurementInfo(0));
                Response r = house.serverAdministrator.path(Constant.Path.UPDATE)
                        .request(MediaType.APPLICATION_OCTET_STREAM)
                        .put(Entity.entity(listProto.toByteArray(), MediaType.APPLICATION_OCTET_STREAM));
                if (r.getStatus() != Response.Status.OK.getStatusCode()) {
                    //LOGGER.warning("Something went wrong communicating the measurements to the server");
                }
            }

            if (listProto != null && listProto.getMeasurementInfo(0).getValue() != 0) {
                //LOGGER.info("\nGlobal Mean:" + listProto.getMeasurementInfo(0).getValue() + "\n");
                System.out.println("\nGlobal Mean:"+listProto.getMeasurementInfo(0).getValue()+"\n");
            }
        }
    }

}
