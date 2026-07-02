package sh.zolt.build;

public final class RunPackageException extends RuntimeException {
    public RunPackageException(String message) {
        super(message);
    }

    public RunPackageException(String message, Throwable cause) {
        super(message, cause);
    }
}
