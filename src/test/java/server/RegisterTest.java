package server;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.junit.jupiter.api.Test;
import proto.HouseProtoMessage;
import util.Constant;

import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;
import java.io.IOException;
import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class RegisterTest {

    @Test
    void removeHouse() throws IOException {
        Server.mockRegisterPopulation();


        URI baseUri = UriBuilder.fromUri("http://"+ Constant.DEFAULT_SERVER_IP).port(Constant.DEFAULT_SERVER_PORT).build();
        ResourceConfig config = new ResourceConfig();
        config.packages("server.services");
        HttpServer server = GrizzlyHttpServerFactory.createHttpServer(baseUri, config);
        server.start();


        WebTarget c = ClientBuilder.newBuilder().build().target(UriBuilder.fromUri("http://"+ Constant.DEFAULT_SERVER_IP).port(Constant.DEFAULT_SERVER_PORT).build()).path(Constant.Path.HOUSES);

        Response response = c.path(Constant.Path.ADD)
                .request(MediaType.APPLICATION_OCTET_STREAM).post(Entity.entity(HouseProtoMessage.HouseInfoProto.newBuilder()
                .setId("abc")
                .build().toByteArray(), MediaType.APPLICATION_OCTET_STREAM));

        System.out.println(response);

        response = c.path(Constant.Path.DELETE+"/"+"abc")
                .request(MediaType.APPLICATION_OCTET_STREAM).delete();

        System.out.println(response);
    }
}