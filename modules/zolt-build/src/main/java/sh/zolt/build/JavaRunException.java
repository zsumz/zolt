package sh.zolt.build;

public final class JavaRunException extends RuntimeException {
    public JavaRunException(String message) {
        super(message);
    }

    public JavaRunException(String message, Throwable cause) {
        super(message, cause);
    }
}
