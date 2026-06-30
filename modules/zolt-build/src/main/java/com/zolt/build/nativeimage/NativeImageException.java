package com.zolt.build.nativeimage;

import com.zolt.error.ActionableError;
import com.zolt.error.HasActionableError;

public final class NativeImageException extends RuntimeException implements HasActionableError {
    private final transient ActionableError error;

    public NativeImageException(String message) {
        super(message);
        this.error = null;
    }

    public NativeImageException(String message, Throwable cause) {
        super(message, cause);
        this.error = null;
    }

    public NativeImageException(ActionableError error) {
        super(error.message(), error.cause().orElse(null));
        this.error = error;
    }

    @Override
    public ActionableError actionableError() {
        return error;
    }
}
