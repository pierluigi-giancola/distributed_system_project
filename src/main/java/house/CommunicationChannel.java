package house;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import proto.HouseProtoMessage.HouseInfoProto;
import proto.rpc.HouseGrpc;
import proto.rpc.HouseGrpc.HouseStub;
import proto.rpc.HouseGrpc.HouseBlockingStub;

import java.util.Objects;
import java.util.concurrent.TimeUnit;


public class CommunicationChannel {

    private volatile ManagedChannel channel;
    private HouseBlockingStub blockingStub;
    private HouseStub asynchStub;
    private HouseInfoProto infoProto;

    public CommunicationChannel() {
        channel = null;
        infoProto = null;
    }

    public synchronized HouseBlockingStub getBlockingStub() {
        return blockingStub;
    }

    public synchronized HouseStub getAsynchStub() { return asynchStub; }

    public synchronized void setChannel(HouseInfoProto infoProto) {
        this.infoProto = infoProto;
        this.channel = ManagedChannelBuilder.forAddress(infoProto.getIp(), infoProto.getPort()).usePlaintext().build();
        this.blockingStub = HouseGrpc.newBlockingStub(channel);
        this.asynchStub = HouseGrpc.newStub(channel);
    }

    public synchronized HouseInfoProto getInfo() {
        return infoProto;
    }

    public boolean isDown() {
        return channel == null;
    }

    public void reset() {
        channel = null;
        infoProto = null;
    }



    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null) return false;
        if (o.getClass() == HouseInfoProto.class) {
            HouseInfoProto info = (HouseInfoProto) o;
            return this.infoProto.equals(info);
        }
        if (getClass() != o.getClass()) return false;
        CommunicationChannel that = (CommunicationChannel) o;
        return this.infoProto.equals(that.infoProto);
    }

    @Override
    public int hashCode() {
        return Objects.hash(infoProto);
    }

    @Override
    public String toString() {
        return "CommunicationChannel{" +
                "infoProto=" + infoProto +
                '}';
    }


}
