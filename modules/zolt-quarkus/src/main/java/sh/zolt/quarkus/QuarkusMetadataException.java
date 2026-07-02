package sh.zolt.quarkus;

public final class QuarkusMetadataException extends RuntimeException {
    public QuarkusMetadataException(String message) {
        super(message);
    }

    public QuarkusMetadataException(String message, Throwable cause) {
        super(message, cause);
    }
}
