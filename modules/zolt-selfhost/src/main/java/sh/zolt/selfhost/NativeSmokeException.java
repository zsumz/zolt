package sh.zolt.selfhost;

public final class NativeSmokeException extends RuntimeException {
    public NativeSmokeException(String message) {
        super(message);
    }

    public NativeSmokeException(String message, Throwable cause) {
        super(message, cause);
    }
}
