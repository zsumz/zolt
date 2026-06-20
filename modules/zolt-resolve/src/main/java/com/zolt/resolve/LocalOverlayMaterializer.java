package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.MavenRepositoryPathBuilder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

final class LocalOverlayMaterializer {
    private final LocalArtifactCache cache;
    private final Map<String, String> artifactSources;
    private final MavenRepositoryPathBuilder repositoryPathBuilder = new MavenRepositoryPathBuilder();

    LocalOverlayMaterializer(LocalArtifactCache cache, Map<String, String> artifactSources) {
        this.cache = Objects.requireNonNull(cache, "cache");
        this.artifactSources = Objects.requireNonNull(artifactSources, "artifactSources");
    }

    Optional<CachedArtifact> materializePom(List<RepositoryOverlay> overlays, Coordinate coordinate) {
        for (RepositoryOverlay overlay : overlays) {
            if (overlay.kind() != RepositoryOverlayKind.MAVEN_LOCAL) {
                continue;
            }
            Path sourcePath = overlay.root().resolve(repositoryPathBuilder.pomPath(coordinate)).normalize();
            if (!Files.isRegularFile(sourcePath)) {
                continue;
            }
            CachedArtifact artifact = cache.materializeOverlayPom(coordinate, overlay.id(), sourcePath);
            artifactSources.put(artifact.repositoryPath(), overlay.lockfileSource());
            return Optional.of(artifact);
        }
        return Optional.empty();
    }

    Optional<CachedArtifact> materializeArtifact(List<RepositoryOverlay> overlays, ArtifactDescriptor descriptor) {
        for (RepositoryOverlay overlay : overlays) {
            if (overlay.kind() != RepositoryOverlayKind.MAVEN_LOCAL) {
                continue;
            }
            Path sourcePath = overlay.root().resolve(repositoryPathBuilder.artifactPath(descriptor)).normalize();
            if (!Files.isRegularFile(sourcePath)) {
                continue;
            }
            CachedArtifact artifact = cache.materializeOverlayArtifact(descriptor, overlay.id(), sourcePath);
            artifactSources.put(artifact.repositoryPath(), overlay.lockfileSource());
            return Optional.of(artifact);
        }
        return Optional.empty();
    }
}
