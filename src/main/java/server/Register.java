package server;

import proto.HouseProtoMessage.*;
import proto.wrapper.HouseInfo;
import proto.AdminProtoMessage.*;
import util.Constant;

import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class Register {

    private final static Logger LOGGER = Logger.getLogger(Register.class.getName());

    private final Set<HouseInfo> activeRegister;

    private final Map<String, List<MeasurementProto>> statistics;

    //volatile for guaranteed write before read
    private static volatile Register instance = null;

    private Register() {
        this.activeRegister = new HashSet<>();
        this.statistics = new HashMap<>();
        //add the condom reserved empty id
        this.statistics.put(Constant.CONDOM_ID, new ArrayList<>());
    }

    //Double Check Locking
    public static Register getInstance() {
        if (instance == null) {
            synchronized (Register.class) {
                if (instance == null) {
                    instance = new Register();
                }
            }
        }
        return instance;
    }

    //Return the shallow copy of the statistics
    private Map<String, List<MeasurementProto>> getStatistics() {
        synchronized (statistics) {
            return new HashMap<>(statistics);
        }
    }

    public List<HouseInfoProto> getHouses() {
        synchronized (activeRegister) {
            return this.activeRegister.stream()
                    .map(HouseInfo::get)
                    .collect(Collectors.toList());
        }
    }

    /**
     * Add the house to the list of houses in the network.
     * Return true if it's added, false if another house with the same id already exist
     * @param h the house to add
     * @return true if it's added, false if another house with the same id already exist
     */
    public List<HouseInfoProto> addHouse(HouseInfoProto h) {
        boolean isAdded;
        synchronized (activeRegister) {
            isAdded = activeRegister.add(new HouseInfo(h));

            if(isAdded) {
                synchronized (statistics) {
                    statistics.put(h.getId(), new ArrayList<>());
                }
                return getHouses();
            } else {
                return new ArrayList<>();
            }
        }
    }

    /**
     * Remove the house from the list ho houses in the network.
     * Return true if it's removed, false otherwise
     * @param houseId the id of house to remove
     * @return true if it's removed, false otherwise
     */
    public boolean removeHouse(String houseId) {
        boolean isRemoved;
        synchronized (activeRegister) {
            isRemoved = activeRegister.remove(new HouseInfo(HouseInfoProto.newBuilder().setId(houseId).build()));
            if (isRemoved) {
                synchronized (statistics) {
                    statistics.remove(houseId);
                }
            }
            return isRemoved;
        }
    }

    /**
     * Add more recent measurement.
     * @param list of the measurement
     * @return the list of the measurement not added to the server (cause the houses associated to them isn't present)
     */
    public List<MeasurementInfoProto> updateStatistics(MeasurementInfoListProto list) {
        List<MeasurementInfoProto> nonExistingHouses = new ArrayList<>();
        synchronized (statistics) {
            list.getMeasurementInfoList()
                    .forEach(measure -> {
                        if(statistics.containsKey(measure.getId())) {
                            statistics.get(measure.getId()).add(MeasurementProto.newBuilder()
                                    .setTimestamp(measure.getTimestamp())
                                    .setValue(measure.getValue())
                                    .build());
                        } else {
                            nonExistingHouses.add(measure);
                        }
                    });
        }
        return nonExistingHouses;
    }

    /**
     * Return an Optional containing a shallow copy of the list of the MeasurementProto of the specified house
     * @param houseId the id of the house
     * @return Optional.empty() if no house with houseId exist; Optional with the list otherwise
     */
    public Optional<List<MeasurementProto>> getMeasurements(String houseId) {
        return Optional.ofNullable(getStatistics().get(houseId));
    }
}