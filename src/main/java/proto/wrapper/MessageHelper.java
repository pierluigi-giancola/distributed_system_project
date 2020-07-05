package proto.wrapper;

import house.LogicalClock;
import proto.rpc.GrpcHouseProto.MessageProto;

public class MessageHelper {

    public enum TypeValue {
        NONE,
        ENTER,
        QUIT,
        MEAN
    }

    public enum ControlValue {
        ACK,
        NACK,
        UPDATE_SUCCESSOR,
        UPDATE_PREDECESSOR,
        SYNCHRO,
        CROWN
    }

    private final String id;

    private final LogicalClock clock;

    public MessageHelper(String id) {
        this.id = id;
        clock = new LogicalClock();
    }

    public MessageProto getGenericMessage() {
        synchronized (clock) {
            return MessageProto.newBuilder()
                    .setTimestamp(clock.getClock())
                    .setId(id)
                    .build();
        }
    }

    public MessageProto getTypedMessage(TypeValue type) {
        synchronized (clock) {
            return MessageProto.newBuilder()
                    .setTimestamp(clock.getClock())
                    .setId(id)
                    .setType(type.ordinal())
                    .build();
        }
    }

    public MessageProto getControlMessage(ControlValue control) {
        synchronized (clock) {
            return MessageProto.newBuilder()
                    .setTimestamp(clock.getClock())
                    .setId(id)
                    .setControl(control.ordinal())
                    .build();
        }
    }

    public MessageProto reSetTimer(MessageProto m) {
        return  MessageProto.newBuilder(m)
                .setTimestamp(getClock())
                .build();
    }

    public long getClock() {
        synchronized (clock) {
            return clock.getClock();
        }
    }

    public void update(MessageProto msg) {
        synchronized (clock) {
            clock.setClock(msg.getTimestamp());
        }
    }
}
