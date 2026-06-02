package com.zolt.cache;

import com.zolt.maven.Coordinate;
import java.nio.file.Path;

public record CachedArtifact(
        Coordinate coordinate,
        String repositoryPath,
        Path cachePath,
        byte[] bytes) {
    public CachedArtifact {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
