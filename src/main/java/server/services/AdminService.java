package server.services;


import proto.AdminProtoMessage.*;
import server.Register;
import util.Constant;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import static java.lang.Math.pow;

@Path(Constant.Path.ADMIN_CONDOM)
public class AdminService {

    /**
     * Get the id of all the houses currently in the network
     * @return Response OK always, containing the id of all the house currently in the network
     */
    @GET
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getHouses() {
        return Response.ok(HouseIdListProto.newBuilder()
                .addAllHouse(Register.getInstance().getHouses().stream()
                        .map(x -> HouseIdProto.newBuilder().setId(x.getId()).build())
                        .collect(Collectors.toList()))
                .build().toByteArray()).build();
    }


    private List<MeasurementProto> getLastNMeasurements(List<MeasurementProto> list, int num) {
        return list.stream()
                .sorted((x,y) -> Long.compare(y.getTimestamp(), x.getTimestamp()))
                .collect(Collectors.toList())
                .subList(0, num > list.size() ? list.size() : num);
    }

    /**
     * Get the last, ordered by most recent timestamp, num measurements of the houseId house
     * @param houseId the id of the house
     * @param num the number of the most recent measurement, based on timestamp
     * @return Response OK if correctly terminated, it will contain a byte array that is originated from AdminProtoMessage.MeasurementListProto.
     *  CONFLICT if no house with the id exists.
     */
    @GET
    @Path(Constant.Path.LAST_MEASUREMENTS)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getLastMeasurements(@PathParam(Constant.Path.HOUSE_ID_PARAM) String houseId, @PathParam(Constant.Path.NUM_PARAM) int num) {
        //for the regex contained in Constant.Path.LAST_MEASUREMENTS the houseId param can be empty or contain an id+"/"
        //in case is empty or has length 1 (which means it contain only "/") we assume the client want to get the condom param.
        houseId = houseId.length() <= 1 ? "" : houseId.substring(0, houseId.length() - 1);
        Optional<List<MeasurementProto>> oList = Register.getInstance().getMeasurements(houseId);
        if (oList.isPresent()) {
            return Response.ok(MeasurementListProto.newBuilder()
                    .addAllMeasurement(getLastNMeasurements(oList.get(), num))
                    .build().toByteArray()).build();
        }
        return Response.status(Response.Status.CONFLICT).build();
    }

    /**
     * Get the statistics based on the last, ordered by most recent timestamp, num measurements of the houseId house
     * @param houseId the id of the house
     * @param num the number of the most recent measurement, based on timestamp
     * @return Response OK if correctly terminated, it will contain a byte array that is originated from AdminProtoMessage.StatisticProto.
     *  CONFLICT if no house with the id exists.
     */
    @GET
    @Path(Constant.Path.LAST_STATISTICS)
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    public Response getLastStatistics(@PathParam(Constant.Path.HOUSE_ID_PARAM) String houseId, @PathParam(Constant.Path.NUM_PARAM) int num) {
        //for the regex contained in Constant.Path.LAST_MEASUREMENTS the houseId param can be empty or contain an id+"/"
        //in case is empty or has length 1 (which means it contain only "/") we assume the client want to get the condom param.
        houseId = houseId.length() <= 1 ? "" : houseId.substring(0, houseId.length() - 1);
        Optional<List<MeasurementProto>> oList = Register.getInstance().getMeasurements(houseId);
        if (oList.isPresent()) {
            List<MeasurementProto> list = getLastNMeasurements(oList.get(), num);
            double arithmeticMean = list.stream()
                    .map(MeasurementProto::getValue)
                    .reduce(Double::sum)
                    .orElse(0d) / list.size();
            double standardDev = Math.sqrt(list.stream()
                    .map(x -> pow(x.getValue() - arithmeticMean, 2))
                    .reduce(Double::sum)
                    .orElse(0d) / (list.size() - 1));
            return Response.ok(StatisticProto.newBuilder()
                    .setArithmeticMean(arithmeticMean)
                    .setStandardDeviation(standardDev)
                    .build().toByteArray()).build();
        }
        return Response.status(Response.Status.CONFLICT).build();
    }
}
