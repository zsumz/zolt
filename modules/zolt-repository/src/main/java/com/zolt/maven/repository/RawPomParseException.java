package com.zolt.maven.repository;

final class RawPomParseException extends RuntimeException {
    public RawPomParseException(String message) {
        super(message);
    }

    public RawPomParseException(String message, Throwable cause) {
        super(message, cause);
    }
}
