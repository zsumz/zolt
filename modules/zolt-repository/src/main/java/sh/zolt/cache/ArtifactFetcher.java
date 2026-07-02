package sh.zolt.cache;

import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.RepositoryArtifact;

@FunctionalInterface
public interface ArtifactFetcher {
    RepositoryArtifact fetch(Coordinate coordinate);
}
