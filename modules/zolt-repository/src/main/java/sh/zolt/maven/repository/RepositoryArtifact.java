package sh.zolt.maven.repository;

import sh.zolt.maven.Coordinate;
import java.net.URI;

public record RepositoryArtifact(
        Coordinate coordinate,
        String path,
        URI source,
        byte[] bytes) {
    public RepositoryArtifact {
        bytes = bytes.clone();
    }

    @Override
    public byte[] bytes() {
        return bytes.clone();
    }
}
