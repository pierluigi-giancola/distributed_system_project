package util;

import java.util.List;

public class RandomPicker<E> {
    public E remove(List<E> list) {
        return list.remove((int) ((100*Math.random()) % list.size()));
    }
}
