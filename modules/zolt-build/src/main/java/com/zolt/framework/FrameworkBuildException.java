package com.zolt.framework;

public final class FrameworkBuildException extends RuntimeException {
    public FrameworkBuildException(String message) {
        super(message);
    }

    public FrameworkBuildException(String message, Throwable cause) {
        super(message, cause);
    }
}
