package com.zolt.lockfile;

public final class LockfileReadException extends RuntimeException {
    public LockfileReadException(String message) {
        super(message);
    }

    public LockfileReadException(String message, Throwable cause) {
        super(message, cause);
    }
}
