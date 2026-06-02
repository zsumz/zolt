package com.zolt.resolve;

public final class ResolveException extends RuntimeException {
    public ResolveException(String message) {
        super(message);
    }

    public ResolveException(String message, Throwable cause) {
        super(message, cause);
    }
}
