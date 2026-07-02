package sh.zolt.build;

import sh.zolt.error.ActionableError;
import sh.zolt.error.HasActionableError;

public final class PackageException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public PackageException(String message) {
        super(message);
        this.error = null;
    }

    public PackageException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public PackageException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static PackageException actionable(String summary, String remediation) {
        return new PackageException(ActionableError.of(summary, remediation));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
