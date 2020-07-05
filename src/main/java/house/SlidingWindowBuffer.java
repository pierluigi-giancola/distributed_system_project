package house;

import provided.Buffer;
import provided.Measurement;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class SlidingWindowBuffer implements Buffer {

    private Measurement[] measurements;
    private double overlap;
    private int head = 0;
    private final List<Measurement> mean = new ArrayList<>();


    public SlidingWindowBuffer(int capacity, double overlap) {
        this.measurements = new Measurement[capacity];
        this.overlap = overlap;
    }

    @Override
    public void addMeasurement(Measurement m) {
        Optional<Measurement[]> ret = push(m);
        ret.ifPresent(this::calcMean);
    }

    private synchronized Optional<Measurement[]> push(Measurement m) {
        measurements[head++] = m;
        if(head == measurements.length) {
            Measurement[] ret = Arrays.copyOf(measurements, measurements.length);
            head = (int) (head * overlap);
            measurements = Arrays.copyOfRange(measurements, head, measurements.length + head);
            return Optional.of(ret);
        }
        return Optional.empty();
    }


    private void calcMean(Measurement[] data) {
        double singleMean = Arrays.stream(data).map(Measurement::getValue).reduce(Double::sum).orElse(0d) / data.length;
        Measurement lastMeasure = data[data.length-1];
        lastMeasure.setValue(singleMean);
        synchronized (mean) {
            mean.add(lastMeasure);
            mean.notify();
        }
    }

    public Measurement getMeasurement() {
        synchronized (mean) {
            while(mean.isEmpty()) {
                try {
                    mean.wait();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
            return mean.remove(0);
        }
    }

}
