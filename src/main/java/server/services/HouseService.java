package server.services;

import com.google.protobuf.InvalidProtocolBufferException;
import proto.HouseProtoMessage.*;
import provided.Measurement;
import server.Register;
import util.Constant;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;

@Path(Constant.Path.HOUSES)
public class HouseService {


    private Response houseMessageProcessor(ProcessMessageSupplier<Optional<Response>, InvalidProtocolBufferException> action) {
        try{
            return action.get().orElse(Response.status(Response.Status.CONFLICT).build());
        } catch (InvalidProtocolBufferException e) {
            // cant parse from the right object from the byte array
            // or the byte array is empty and I don't want to parse it (it will generate a object with all default values)
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
    /**
     * Add the house
     * @param houseInfo as a byte array that should be originated from object is HouseProtoMessage.HouseInfoProto
     * @return Response OK if correctly terminated, it will contain a byte array that is originated from HouseProtoMessage.HouseInfoListProto.
     *  BAD_REQUEST if the byte array in input is corrupted for any reason.
     *  CONFLICT if another house with the same id already exist.
     */
    @POST
    @Path(Constant.Path.ADD)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response addHouse(byte[] houseInfo) {
        return houseMessageProcessor(() -> {
            if(houseInfo.length == 0) throw new InvalidProtocolBufferException("");
            List<HouseInfoProto> list = Register.getInstance().addHouse(HouseInfoProto.parseFrom(houseInfo));
            if(!list.isEmpty()) {
                return Optional.of(Response.ok(HouseInfoListProto.newBuilder()
                        .addAllHouse(list)
                        .build().toByteArray()).build());
            }
            return Optional.empty();
        });
    }

    /**
     * Remove the house
     * @param id of the house to delete
     * @return Response OK if correctly terminated
     *  CONFLICT if no house with the id exists.
     */
    @DELETE
    @Path("delete/{id}")
    public Response deleteHouse(@PathParam("id") String id) {
        return houseMessageProcessor(() -> {
            if(Register.getInstance().removeHouse(id)) {
                return Optional.of(Response.ok().build());
            }
            return Optional.empty();
        });
    }

    /**
     * Update the measurement
     * @param measurementList as a byte array that should be originated from object HouseProtoMessage.MeasurementInfoListProto
     * @return Response OK if correctly terminated, it will contain a byte array that is originated
     * from HouseProtoMessage.MeasurementInfoListProto representing the measurements that wasn't updated (cause the houses associated to them isn't present).
     *  BAD_REQUEST if the byte array in input is corrupted for any reason.
     */
    @PUT
    @Path(Constant.Path.UPDATE)
    @Consumes(MediaType.APPLICATION_OCTET_STREAM)
    public Response updateMeasurement(byte[] measurementList) {
        return houseMessageProcessor(() -> {
            if(measurementList.length == 0) throw new InvalidProtocolBufferException("");
            List<MeasurementInfoProto> nonUpdated = Register.getInstance().updateStatistics(MeasurementInfoListProto.parseFrom(measurementList));
            return Optional.of(Response.ok(MeasurementInfoListProto.newBuilder()
                    .addAllMeasurementInfo(nonUpdated)
                    .build().toByteArray()).build());
        });
    }
}
