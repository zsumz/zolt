package com.zolt.build;

public final class PackageException extends RuntimeException {
    public PackageException(String message) {
        super(message);
    }

    public PackageException(String message, Throwable cause) {
        super(message, cause);
    }
}
