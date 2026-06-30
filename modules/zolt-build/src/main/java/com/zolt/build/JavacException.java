package com.zolt.build;

import com.zolt.error.ActionableError;
import com.zolt.error.HasActionableError;

public final class JavacException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public JavacException(String message) {
        super(message);
        this.error = null;
    }

    public JavacException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public JavacException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    public static JavacException actionable(String summary, String remediation) {
        return new JavacException(ActionableError.of(summary, remediation));
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
