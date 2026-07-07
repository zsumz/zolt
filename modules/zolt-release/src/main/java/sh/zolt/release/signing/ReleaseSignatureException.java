package sh.zolt.release.signing;

public final class ReleaseSignatureException extends RuntimeException {
    public ReleaseSignatureException(String message) {
        super(message);
    }

    public ReleaseSignatureException(String message, Throwable cause) {
        super(message, cause);
    }
}
