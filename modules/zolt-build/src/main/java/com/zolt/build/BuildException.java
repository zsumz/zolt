package com.zolt.build;

import com.zolt.error.ActionableError;
import com.zolt.error.HasActionableError;

public final class BuildException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public BuildException(String message) {
        super(message);
        this.error = null;
    }

    public BuildException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public BuildException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static BuildException actionable(String summary, String remediation) {
        return new BuildException(ActionableError.of(summary, remediation));
    }

    public static BuildException actionable(String summary, String remediation, Throwable cause) {
        return new BuildException(ActionableError.of(summary, remediation, cause));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
