package util;

public class GenericWrapper<T> {

    private T obj;

    public GenericWrapper() {this.obj = null; }

    public GenericWrapper(T obj) {
        this.obj = obj;
    }

    public T getObj() {
        return obj;
    }

    public void setObj(T obj) {
        this.obj = obj;
    }

    public boolean isPresent() { return this.obj != null; }

    @Override
    public String toString() {
        return "GenericWrapper{" +
                "obj=" + obj +
                '}';
    }
}
