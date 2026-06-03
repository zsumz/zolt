package com.zolt.release;

public final class ReleaseVerificationException extends RuntimeException {
    public ReleaseVerificationException(String message) {
        super(message);
    }

    public ReleaseVerificationException(String message, Throwable cause) {
        super(message, cause);
    }
}
