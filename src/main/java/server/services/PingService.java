package server.services;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@Path("")
public class PingService {
    @GET
    public Response ping() {
        return Response.ok().build();
    }
}
