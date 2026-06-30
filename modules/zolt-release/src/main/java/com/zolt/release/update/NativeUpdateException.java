package com.zolt.release.update;

public final class NativeUpdateException extends RuntimeException {
    public NativeUpdateException(String message) {
        super(message);
    }

    public NativeUpdateException(String message, Throwable cause) {
        super(message, cause);
    }
}
