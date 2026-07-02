package sh.zolt.framework;

public final class FrameworkRunException extends RuntimeException {
    public FrameworkRunException(String message) {
        super(message);
    }

    public FrameworkRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
