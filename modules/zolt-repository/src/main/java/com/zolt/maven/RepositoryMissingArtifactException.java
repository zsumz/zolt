package com.zolt.maven;

public final class RepositoryMissingArtifactException extends RepositoryClientException {
    public RepositoryMissingArtifactException(String message) {
        super(message);
    }
}
