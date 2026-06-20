package com.zolt.build;

public final class NativeImageException extends RuntimeException {
    public NativeImageException(String message) {
        super(message);
    }

    public NativeImageException(String message, Throwable cause) {
        super(message, cause);
    }
}
