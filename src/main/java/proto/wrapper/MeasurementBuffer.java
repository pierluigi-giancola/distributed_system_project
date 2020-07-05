package proto.wrapper;

import proto.HouseProtoMessage.MeasurementInfoListProto;
import proto.HouseProtoMessage.MeasurementInfoProto;
import util.Constant;

import java.util.*;
import java.util.stream.Collectors;

public class MeasurementBuffer {

    public final static double EXCLUDED_VALUE = Double.MIN_VALUE;

    private Map<String, Queue<MeasurementInfoProto>> buffer = new HashMap<>();

    private final List<MeasurementInfoProto> pendingMsg = new ArrayList<>();

    private volatile double lastMeasure = 0;

    private List<MeasurementInfoProto> lastMeasureBuffer = new ArrayList<>();

    private String myId = null;


    public void initialize(List<MeasurementInfoProto> list, double lastMeasure) {
        buffer = list.stream().collect(Collectors.toMap(MeasurementInfoProto::getId,
                y -> {
                    Queue<MeasurementInfoProto> q = new PriorityQueue<>(Comparator.comparingLong(MeasurementInfoProto::getTimestamp));
                    q.add(y);
                    return q;
                },
                (y1, y2) -> {
                    y1.addAll(y2);
                    return y1;
                }));
        this.lastMeasure = lastMeasure;
    }


    public static MeasurementInfoProto getFakeMeasure(String id) {
        return MeasurementInfoProto.newBuilder()
                .setId(id)
                .setTimestamp(0)
                .setValue(MeasurementBuffer.EXCLUDED_VALUE)
                .build();
    }

    public void addMeasure(MeasurementInfoProto m) {
        Queue<MeasurementInfoProto> q = new PriorityQueue<>(Comparator.comparingLong(MeasurementInfoProto::getTimestamp));
        q.add(m);
        synchronized (buffer) {
            buffer.merge(m.getId(), q, (y1,y2) -> {
                y1.addAll(y2);
                return y1;
            });
        }
    }

    public void addPendingMeasure(MeasurementInfoProto m) {
        if (myId == null) {
            myId = m.getId();
        }
        synchronized (pendingMsg) {
            pendingMsg.add(m);
        }
    }

    public void removePendingMeasure(MeasurementInfoProto m) {
        MeasurementInfoProto mm = null;
        synchronized (pendingMsg) {
            int i = pendingMsg.indexOf(m);
            if (i >= 0) {
                mm = pendingMsg.remove(i);
            }
        }
        assert mm != null;
        this.addMeasure(mm);
        //Notify for wake up when I'm waiting on this queue for quitting
        synchronized (pendingMsg) {
            pendingMsg.notify();
        }
    }

    public List<MeasurementInfoProto> getPendingMsg() {
        return pendingMsg;
    }

    public boolean atLeastTwoConfirmed() {
        synchronized (buffer) {
            return myId != null && buffer.containsKey(myId) && buffer.get(myId).size() > 1;
        }
    }

    public synchronized List<MeasurementInfoProto> getFullBuffer() {
        List<MeasurementInfoProto> ret = getConfirmedBuffer();
        ret.addAll(pendingMsg);
        return ret;
    }

    private synchronized List<MeasurementInfoProto> getConfirmedBuffer() {
        return buffer.entrySet().stream().flatMap(x -> x.getValue().stream()).collect(Collectors.toList());
    }

    public List<MeasurementInfoProto> getMinBuffer() {
        return buffer.values().stream().map(Queue::peek).filter(Objects::nonNull).collect(Collectors.toList());
    }

    public List<MeasurementInfoProto> getLastMeasureBuffer() {
        return new ArrayList<>(lastMeasureBuffer);
    }

    public double getLastConsumption() {
        return lastMeasure;
    }


    public MeasurementInfoListProto getCondomConsumption(boolean withMeasure) {
        synchronized (buffer) {
            List<MeasurementInfoProto> measures = new ArrayList<>();
            //Remove all the empty Queue, assume they no Longer stay in the Network
            buffer.entrySet().removeIf(e -> e.getValue().isEmpty());
            long maxTs = buffer.values().stream().map(q -> q.element().getTimestamp()).max(Long::compareTo).orElse(0L);
            lastMeasure = buffer.values().stream()
                    .map(Queue::poll)
                    .peek(measures::add)
                    .map(MeasurementInfoProto::getValue)
                    .filter(v -> v != EXCLUDED_VALUE)
                    .reduce(Double::sum).orElse(0d);
            lastMeasureBuffer = measures;
            measures.stream().filter(x -> x.getValue() != EXCLUDED_VALUE).forEach((x) -> System.out.println(x.getId() +"->"+ x.getValue()));
            return MeasurementInfoListProto.newBuilder()
                    .addMeasurementInfo(MeasurementInfoProto.newBuilder()
                            .setId(Constant.CONDOM_ID)
                            .setTimestamp(maxTs)
                            .setValue(lastMeasure)
                            .build())
                    .addAllMeasurementInfo(withMeasure ? measures : Collections.emptyList())
                    .build();
        }
    }

}
