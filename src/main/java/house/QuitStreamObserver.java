package house;

import io.grpc.stub.StreamObserver;
import proto.HouseProtoMessage;
import proto.rpc.GrpcHouseProto;
import proto.wrapper.MessageHelper;
import util.GenericWrapper;

import java.util.logging.Logger;

public class QuitStreamObserver implements StreamObserver<GrpcHouseProto.MessageProto> {

    private final static Logger LOGGER = Logger.getLogger(QuitStreamObserver.class.getName());

    final private HouseProtoMessage.HouseInfoProto snapTarget;
    final private CommunicationChannel target;
    final private GenericWrapper<Integer> ack;
    final private GrpcHouseProto.HouseInfoMessageProto sendMsg;
    private StreamObserver<GrpcHouseProto.HouseInfoMessageProto> toTarget;


    public QuitStreamObserver(CommunicationChannel target, GrpcHouseProto.HouseInfoMessageProto msg, GenericWrapper<Integer> ack) {
        this.target = target;
        this.snapTarget = target.getInfo();
        this.ack = ack;
        this.sendMsg = msg;
    }


    public void sendFirstMessage() {
        toTarget = target.getAsynchStub().bye(this);
        toTarget.onNext(sendMsg);
    }

    public void completeQuit(GrpcHouseProto.HouseInfoMessageProto msg) {
        toTarget.onNext(msg);
        toTarget.onCompleted();
    }

    public void onNext(GrpcHouseProto.MessageProto messageProto) {
        if (messageProto.getControl() != MessageHelper.ControlValue.ACK.ordinal()) {
            synchronized (target) {
                if(target.equals(snapTarget)) {
                    LOGGER.info("Target is quitting, his info are :" + target.getInfo());
                    try {
                        target.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
                LOGGER.info("Wake up, new target is:" + target.getInfo());
                this.sendFirstMessage();
            }
        } else {
            synchronized (ack) {
                ack.setObj(ack.getObj()+1);
                ack.notifyAll();
            }
        }
    }

    @Override
    public void onError(Throwable throwable) {

    }

    @Override
    public void onCompleted() {
        synchronized (ack) {
            ack.setObj(ack.getObj()+1);
            ack.notifyAll();
        }
    }
}
