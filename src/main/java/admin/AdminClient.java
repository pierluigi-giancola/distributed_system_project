package admin;

import proto.AdminProtoMessage.*;
import util.Constant;

import javax.ws.rs.ClientErrorException;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class AdminClient {

    public static void main(String... args) {
        Client client = ClientBuilder.newBuilder().build();
        WebTarget target = client.target(Constant.DEFAULT_SERVER_URI);

        System.out.println("Pointing at " + Constant.DEFAULT_SERVER_URI);
        System.out.println("For a list of command digit \"help\"\n");

        BufferedReader inFromUser =
                new BufferedReader(new InputStreamReader(System.in));
        InputStream respEntity;

        while (true) {
            try {
                String command = inFromUser.readLine();

                String[] commandSlice = command.split(" ");

                switch (commandSlice[0]) {
                    case "help":
                        System.out.println("List of Commands");
                        System.out.println("ls -> retrieve from server all the houses id in the network");
                        System.out.println("get <num> m -> retrieve from server the last <num> measurements of the condominium");
                        System.out.println("get <num> s -> retrieve from server the statistics based on the last <num> measurements of the condominium");
                        System.out.println("get <house_id> <num> m -> retrieve from server the last <num> measurements of the house with id == <house_id>");
                        System.out.println("get <house_id> <num> s -> retrieve from server the statistics based on the last <num> measurements of the house with id == <house_id>");
                        break;
                    case "ls":
                        respEntity = target.path(Constant.Path.ADMIN_CONDOM).request(MediaType.APPLICATION_OCTET_STREAM_TYPE).get(InputStream.class);
                        HouseIdListProto houseListId = HouseIdListProto.parseFrom(respEntity);
                        System.out.println("LIST HOUSES ID:");
                        System.out.println(houseListId);
                        break;
                    case "get":
                        int num;
                        String id;
                        String type;
                        if (commandSlice.length == 3) {
                            id = "";
                            num = Integer.valueOf(commandSlice[1]);
                            type = commandSlice[2];
                        } else if (commandSlice.length == 4) {
                            id = commandSlice[1];
                            num = Integer.valueOf(commandSlice[2]);
                            type = commandSlice[3];
                        } else {
                            System.out.println("[ERROR]: invalid number of arguments, digit \"help\" for list of commands");
                            break;
                        }
                        String subject = id.isEmpty() ? "CONDOMINIUM" : "HOUSE " + id;
                        String error = "[ERROR]: no house with id <" + id + "> exists";
                        try {
                            switch (type) {
                                case "m":
                                    respEntity = target.path(Constant.Path.getLastMeasurementsPath(id, num))
                                            .request(MediaType.APPLICATION_OCTET_STREAM_TYPE).get(InputStream.class);
                                    MeasurementListProto measurementList = MeasurementListProto.parseFrom(respEntity);
                                    System.out.println(subject + " LAST " + num + " MEASUREMENTS:");
                                    System.out.println(measurementList);
                                    break;
                                case "s":
                                    respEntity = target.path(Constant.Path.getLastStatisticsPath(id, num))
                                            .request(MediaType.APPLICATION_OCTET_STREAM_TYPE).get(InputStream.class);
                                    StatisticProto statistic = StatisticProto.parseFrom(respEntity);
                                    System.out.println(subject + " STATISTIC FROM LAST " + num + " MEASUREMENTS:");
                                    System.out.println(statistic);
                                    break;
                                default:
                                    System.out.println("[ERROR]: command not exist, digit \"help\" for list of commands");
                            }
                        } catch (ClientErrorException e) {
                            System.out.println("[ERROR]: server response with HTTP " + e.getResponse().getStatus() + " status code.");
                            System.out.println(error);
                        }
                        break;
                    default:
                        System.out.println("[ERROR]: command not exist, digit \"help\" for list of commands");
                }
                System.out.println("----------------\n");

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
