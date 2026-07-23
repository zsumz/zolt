package sh.zolt.maven.repository;

public final class RawPomParseException extends RuntimeException {
    public RawPomParseException(String message) {
        super(message);
    }

    public RawPomParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
