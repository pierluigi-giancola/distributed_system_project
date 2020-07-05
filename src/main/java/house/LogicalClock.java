package house;

public class LogicalClock {

    private volatile Long clock = 0L;

    public Long getClock() {
        synchronized (clock) {
            return clock++;
        }
    }

    public void setClock(Long receivedClock) {
        synchronized (clock) {
            clock = clock < receivedClock ? receivedClock : clock;
        }
    }
}
