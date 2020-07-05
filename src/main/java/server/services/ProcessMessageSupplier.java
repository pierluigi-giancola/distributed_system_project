package server.services;

@FunctionalInterface
public interface ProcessMessageSupplier<T, E extends Exception> {
    T get() throws E;
}
