package com.zolt.build;

public final class JavacException extends RuntimeException {
    public JavacException(String message) {
        super(message);
    }

    public JavacException(String message, Throwable cause) {
        super(message, cause);
    }
}
