package com.zolt.maven;

public class RepositoryClientException extends RuntimeException {
    public RepositoryClientException(String message) {
        super(message);
    }

    public RepositoryClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
