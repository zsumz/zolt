package com.zolt.lockfile.toml;

import com.zolt.error.ActionableError;
import com.zolt.error.HasActionableError;

public final class LockfileReadException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public LockfileReadException(String message) {
        super(message);
        this.error = null;
    }

    public LockfileReadException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public LockfileReadException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static LockfileReadException actionable(String summary, String remediation) {
        return new LockfileReadException(ActionableError.of(summary, remediation));
    }

    public static LockfileReadException actionable(String summary, String remediation, Throwable cause) {
        return new LockfileReadException(ActionableError.of(summary, remediation, cause));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
