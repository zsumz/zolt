package sh.zolt.maven.metadata;

/** Raised when {@code maven-metadata.xml} cannot be parsed as a well-formed, safe version listing. */
public final class MavenMetadataParseException extends RuntimeException {
    public MavenMetadataParseException(String message) {
        super(message);
    }

    public MavenMetadataParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
