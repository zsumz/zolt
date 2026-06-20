package com.zolt.project;

public final class ProjectPathException extends RuntimeException {
    public ProjectPathException(String message) {
        super(message);
    }

    public ProjectPathException(String message, Throwable cause) {
        super(message, cause);
    }
}
