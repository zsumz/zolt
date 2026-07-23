package sh.zolt.quality.coverage;

/** Raised when a Jacoco XML report cannot be read or is structurally invalid. */
public final class CoverageReportException extends RuntimeException {
    public CoverageReportException(String message) {
        super(message);
    }

    public CoverageReportException(String message, Throwable cause) {
        super(message, cause);
    }
}
