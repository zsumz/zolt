package com.zolt.lockfile;

public final class LockfileWriteException extends RuntimeException {
    public LockfileWriteException(String message, Throwable cause) {
        super(message, cause);
    }
}
