package sh.zolt.resolve;

import sh.zolt.error.ActionableError;
import sh.zolt.error.HasActionableError;

public final class ResolveException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public ResolveException(String message) {
        super(message);
        this.error = null;
    }

    public ResolveException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public ResolveException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static ResolveException actionable(String summary, String remediation) {
        return new ResolveException(ActionableError.of(summary, remediation));
    }

    public static ResolveException actionable(String summary, String remediation, Throwable cause) {
        return new ResolveException(ActionableError.of(summary, remediation, cause));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
