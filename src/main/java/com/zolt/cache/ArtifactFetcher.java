package com.zolt.cache;

import com.zolt.maven.Coordinate;
import com.zolt.maven.RepositoryArtifact;

@FunctionalInterface
public interface ArtifactFetcher {
    RepositoryArtifact fetch(Coordinate coordinate);
}
