package util.exception;

public class IdAlreadyUseException extends Exception {

    public IdAlreadyUseException(String id) {
        super("The id: \"" + id + "\" is already use");
    }
}
