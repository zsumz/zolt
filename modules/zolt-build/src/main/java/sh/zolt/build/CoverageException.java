package sh.zolt.build;

public final class CoverageException extends RuntimeException {
    public CoverageException(String message) {
        super(message);
    }

    public CoverageException(String message, Throwable cause) {
        super(message, cause);
    }
}
