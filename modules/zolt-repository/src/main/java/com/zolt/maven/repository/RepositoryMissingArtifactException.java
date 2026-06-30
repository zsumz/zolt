package com.zolt.maven.repository;

public final class RepositoryMissingArtifactException extends RepositoryClientException {
    public RepositoryMissingArtifactException(String message) {
        super(message);
    }
}
