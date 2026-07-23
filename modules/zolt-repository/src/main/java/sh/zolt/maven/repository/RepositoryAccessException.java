package sh.zolt.maven.repository;

import sh.zolt.error.ActionableError;
import sh.zolt.error.HasActionableError;

/**
 * Raised when repository access cannot be planned from configuration — an empty repository list, an
 * unsafe repository URL, or missing/undeclared credentials. Carries an optional {@link
 * ActionableError} through the CLI's existing execution-exception plumbing, mirroring the resolve
 * layer's own actionable exception so error rendering is identical for callers in either module.
 */
public final class RepositoryAccessException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public RepositoryAccessException(String message) {
        super(message);
        this.error = null;
    }

    public RepositoryAccessException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public RepositoryAccessException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static RepositoryAccessException actionable(String summary, String remediation) {
        return new RepositoryAccessException(ActionableError.of(summary, remediation));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
