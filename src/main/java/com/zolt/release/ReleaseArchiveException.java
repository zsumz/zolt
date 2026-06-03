package com.zolt.release;

public final class ReleaseArchiveException extends RuntimeException {
    public ReleaseArchiveException(String message) {
        super(message);
    }

    public ReleaseArchiveException(String message, Throwable cause) {
        super(message, cause);
    }
}
