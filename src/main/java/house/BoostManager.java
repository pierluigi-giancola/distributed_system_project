package house;

import util.Constant;

import java.util.*;
import java.util.stream.IntStream;

public class BoostManager{

    private final Queue<BoostReservation> boostReservations = new PriorityQueue<>(Comparator.comparingInt(x -> x.myCount));

    private final List<String> usedBoostToken = new ArrayList<>();

    private final List<String> boostToken = new ArrayList<>();


    //volatile for guaranteed write before read
    private static volatile BoostManager instance = null;

    private BoostManager() {

    }

    //Double Check Locking
    public static BoostManager getInstance() {
        if (instance == null) {
            synchronized (BoostManager.class) {
                if (instance == null) {
                    instance = new BoostManager();
                }
            }
        }
        return instance;
    }


    public void generateTokens() {
        IntStream.range(0, Constant.BOOST_TOKEN).forEach(x -> boostToken.add(String.valueOf(Math.random())));
    }

    public void addReservation() {
        synchronized (boostReservations) {
            boostReservations.add(new BoostReservation());
            boostReservations.notifyAll();
        }
    }

    private boolean startReservation() {
        synchronized (boostReservations) {
            if(!boostReservations.isEmpty() && !boostReservations.peek().served) {
                boostReservations.peek().served = true;
                return true;
            } else {
                return false;
            }
        }
    }

    public void removeReservation() {
        synchronized (boostReservations) {
            boostReservations.remove();
            boostReservations.notifyAll();
        }
    }

    public List<String> getAllTokens() {
        synchronized (boostToken) {
            synchronized (usedBoostToken) {
                List<String> l = new ArrayList<>(usedBoostToken);
                l.addAll(boostToken);
                usedBoostToken.clear();
                boostToken.clear();
                return l;
            }
        }
    }

    public void addToken(String token, boolean used) {
        if (used) {
            synchronized (usedBoostToken) {
                usedBoostToken.add(token);
            }
        } else {
            synchronized (boostToken) {
                boostToken.add(token);
                boostToken.notifyAll();
            }
        }
    }

    public void quitProcedure() {
        //Empty the boost request except the one that is served
        synchronized (boostReservations) {
            if (boostReservations.isEmpty()) {
                if (boostReservations.peek().isServed()) {
                    BoostReservation save = boostReservations.poll();
                    boostReservations.clear();
                    boostReservations.add(save);
                } else {
                    boostReservations.clear();
                }
            }
        }
        //If the boost reservation isn't empty wait until it becomes empty
        if(!boostReservations.isEmpty()) {
            synchronized (boostReservations) {
                if (!boostReservations.isEmpty()) {
                    try {
                        boostReservations.wait();
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }


    public String getToken() {
        synchronized (boostReservations) {
            while (!startReservation()) {
                try {
                    //LOGGER.info("Wait for Boost Request");
                    boostReservations.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        synchronized (boostToken) {
            while (boostToken.isEmpty()) {
                try {
                    boostToken.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return boostToken.remove(0);
        }

    }


    private static class BoostReservation {
        private static int count = 0;
        private boolean served;
        private final int myCount;

        BoostReservation() {
            this.myCount = BoostReservation.count++;
            this.served = false;
        }

        public boolean isServed() {
            return served;
        }

        @Override
        public String toString() {
            return "BoostReservation{" +
                    "served=" + served +
                    ", myCount=" + myCount +
                    '}';
        }
    }

}
