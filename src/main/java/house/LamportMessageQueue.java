package house;


import proto.rpc.GrpcHouseProto.MessageProto;

import java.util.PriorityQueue;

public class LamportMessageQueue extends PriorityQueue<MessageProto>{

    public LamportMessageQueue() {
        super((x,y) -> x.getTimestamp() == y.getTimestamp() ? x.getId().compareTo(y.getId()) : x.getTimestamp() < y.getTimestamp() ? -1 : 1);
    }

    @Override
    public synchronized boolean add(MessageProto msg){
        return super.add(msg);
    }

    @Override
    public synchronized MessageProto peek() {
        return super.peek();
    }

    @Override
    public synchronized MessageProto poll() {
        return super.poll();
    }

    @Override
    public synchronized boolean remove(Object o) { return super.remove(o); }

}
