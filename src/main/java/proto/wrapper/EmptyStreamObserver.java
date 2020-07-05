package proto.wrapper;

import io.grpc.stub.StreamObserver;

public class EmptyStreamObserver implements StreamObserver {

    @Override
    public void onNext(Object o) {
    }

    @Override
    public void onError(Throwable throwable) {
    }

    @Override
    public void onCompleted() {
    }
}
