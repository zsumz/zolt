package sh.zolt.selfhost;

public final class SelfHostingParityException extends RuntimeException {
    public SelfHostingParityException(String message) {
        super(message);
    }

    public SelfHostingParityException(String message, Throwable cause) {
        super(message, cause);
    }
}
