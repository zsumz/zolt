package com.zolt.build;

public final class ResourceCopyException extends RuntimeException {
    public ResourceCopyException(String message) {
        super(message);
    }

    public ResourceCopyException(String message, Throwable cause) {
        super(message, cause);
    }
}
