package com.zolt.cache;

public final class ArtifactCacheException extends RuntimeException {
    public ArtifactCacheException(String message) {
        super(message);
    }

    public ArtifactCacheException(String message, Throwable cause) {
        super(message, cause);
    }
}
