package com.zolt.build;

public final class CleanException extends RuntimeException {
    public CleanException(String message) {
        super(message);
    }

    public CleanException(String message, Throwable cause) {
        super(message, cause);
    }
}
