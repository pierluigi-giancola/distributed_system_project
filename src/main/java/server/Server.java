package server;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import proto.HouseProtoMessage;
import util.Constant;

import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;

public class Server {

    public static void main(final String... args) {
        URI baseUri = UriBuilder.fromUri("http://"+ Constant.DEFAULT_SERVER_IP).port(Constant.DEFAULT_SERVER_PORT).build();
        ResourceConfig config = new ResourceConfig();
        config.packages("server.services");
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
        try {
            server.start();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void mockRegisterPopulation() {
        final HouseProtoMessage.HouseInfoProto h1 = HouseProtoMessage.HouseInfoProto.newBuilder()
                .setId("abc")
                .setIp("localhost")
                .setPort(123)
                .build();

        final HouseProtoMessage.HouseInfoProto h2 = HouseProtoMessage.HouseInfoProto.newBuilder()
                .setId("def")
                .setIp("localhost")
                .setPort(123)
                .build();

        final HouseProtoMessage.HouseInfoProto h3 = HouseProtoMessage.HouseInfoProto.newBuilder()
                .setId("ghi")
                .setIp("localhost")
                .setPort(123)
                .build();

        final HouseProtoMessage.HouseInfoProto condom = HouseProtoMessage.HouseInfoProto.newBuilder()
                .setId("")
                .setIp("localhost")
                .setPort(123)
                .build();

        final HouseProtoMessage.MeasurementInfoListProto measurements1 = HouseProtoMessage.MeasurementInfoListProto.newBuilder()
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("abc")
                        .setValue(3)
                        .setTimestamp(1)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("def")
                        .setValue(2)
                        .setTimestamp(1)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("ghi")
                        .setValue(5)
                        .setTimestamp(1)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("")
                        .setValue(10/3)
                        .setTimestamp(1)
                        .build())
                .build();

        final HouseProtoMessage.MeasurementInfoListProto measurements2 = HouseProtoMessage.MeasurementInfoListProto.newBuilder()
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("abc")
                        .setValue(4)
                        .setTimestamp(2)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("def")
                        .setValue(6)
                        .setTimestamp(2)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("ghi")
                        .setValue(7)
                        .setTimestamp(2)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("")
                        .setValue(17/3)
                        .setTimestamp(2)
                        .build())
                .build();

        final HouseProtoMessage.MeasurementInfoListProto measurements3 = HouseProtoMessage.MeasurementInfoListProto.newBuilder()
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("abc")
                        .setValue(3)
                        .setTimestamp(3)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("def")
                        .setValue(4)
                        .setTimestamp(3)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("ghi")
                        .setValue(5)
                        .setTimestamp(3)
                        .build())
                .addMeasurementInfo(HouseProtoMessage.MeasurementInfoProto.newBuilder()
                        .setId("")
                        .setValue(4)
                        .setTimestamp(3)
                        .build())
                .build();

        Register.getInstance().addHouse(h1);
        Register.getInstance().addHouse(h2);
        Register.getInstance().addHouse(h3);
        Register.getInstance().updateStatistics(measurements1);
        Register.getInstance().updateStatistics(measurements2);
        Register.getInstance().updateStatistics(measurements3);
    }
}
