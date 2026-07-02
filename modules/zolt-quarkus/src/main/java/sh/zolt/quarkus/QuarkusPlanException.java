package sh.zolt.quarkus;

public final class QuarkusPlanException extends RuntimeException {
    public QuarkusPlanException(String message) {
        super(message);
    }

    public QuarkusPlanException(String message, Throwable cause) {
        super(message, cause);
    }
}
