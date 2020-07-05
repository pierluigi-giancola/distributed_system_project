package house;

import proto.rpc.GrpcHouseProto.MessageProto;
import proto.wrapper.MessageHelper;
import util.GenericWrapper;

import java.util.*;

public class HouseSynchro {

    private final PriorityQueue<MessageProto> topologyQueue = new LamportMessageQueue();

    private final GenericWrapper<String> meanToken;

    private boolean synchroNeededForFirstMessageInNetwork = true;

    public HouseSynchro() {
        meanToken = new GenericWrapper<>();
    }

    public void resolveTopology() {
        synchronized (topologyQueue) {
            topologyQueue.poll();
            if (topologyQueue.isEmpty()) {
                return;
            }
            synchronized (topologyQueue.peek()) {
                topologyQueue.peek().notify();
            }
        }
    }

    public String consumeToken() {
        String s = "";
        synchronized (meanToken) {
            if (meanToken.getObj() != null) {
                s = meanToken.getObj();
                meanToken.setObj(null);
            }
        }
        return s;
    }

    public void waitMeanToken() {
        synchronized (meanToken) {
            if(!meanToken.isPresent()) {
                try {
                    meanToken.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public PriorityQueue<MessageProto> getTopologyQueue() {
        return topologyQueue;
    }

    public GenericWrapper<String> getMeanToken() {
        return meanToken;
    }

    public void setMeanToken(String s) {
        synchronized (meanToken) {
            meanToken.setObj(s);
            meanToken.notify();
        }
    }

    public boolean isQuitting(String id) {
        return !topologyQueue.isEmpty()
                && topologyQueue.peek().getType() == MessageHelper.TypeValue.QUIT.ordinal()
                && topologyQueue.peek().getId().equals(id);
    }


    public boolean isFirstMessage() {
        return synchroNeededForFirstMessageInNetwork;
    }

    public void setSynchroNeed(boolean synchroFlag) {
        this.synchroNeededForFirstMessageInNetwork = synchroFlag;
    }

}
