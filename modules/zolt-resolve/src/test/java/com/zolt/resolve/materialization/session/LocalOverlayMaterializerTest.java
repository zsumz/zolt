package com.zolt.resolve.materialization.session;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.MavenRepositoryPathBuilder;
import com.zolt.resolve.materialization.RepositoryOverlay;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LocalOverlayMaterializerTest {
    private final MavenRepositoryPathBuilder pathBuilder = new MavenRepositoryPathBuilder();

    @TempDir
    private Path tempDir;

    @Test
    void materializesPomFromMavenLocalOverlayAndRecordsSource() throws IOException {
        Coordinate coordinate = coordinate("com.example", "app", "1.0.0");
        byte[] bytes = "<project/>".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        Path mavenLocalRoot = writeMavenLocal(pathBuilder.pomPath(coordinate), bytes);
        Map<String, String> sources = new ConcurrentHashMap<>();

        Optional<CachedArtifact> artifact = materializer(sources)
                .materializePom(List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)), coordinate);

        assertTrue(artifact.isPresent());
        assertArrayEquals(bytes, artifact.orElseThrow().bytes());
        assertEquals("local-overlay:maven-local", sources.get(artifact.orElseThrow().repositoryPath()));
        assertTrue(Files.isRegularFile(artifact.orElseThrow().cachePath()));
    }

    @Test
    void materializesArtifactFromMavenLocalOverlayAndRecordsSource() throws IOException {
        ArtifactDescriptor descriptor = ArtifactDescriptor.jar(
                coordinate("com.example", "app", "1.0.0"),
                Optional.of("tests"));
        byte[] bytes = new byte[] {1, 2, 3};
        Path mavenLocalRoot = writeMavenLocal(pathBuilder.artifactPath(descriptor), bytes);
        Map<String, String> sources = new ConcurrentHashMap<>();

        Optional<CachedArtifact> artifact = materializer(sources)
                .materializeArtifact(List.of(RepositoryOverlay.mavenLocal(mavenLocalRoot)), descriptor);

        assertTrue(artifact.isPresent());
        assertArrayEquals(bytes, artifact.orElseThrow().bytes());
        assertEquals("local-overlay:maven-local", sources.get(artifact.orElseThrow().repositoryPath()));
        assertTrue(Files.isRegularFile(artifact.orElseThrow().cachePath()));
    }

    @Test
    void returnsEmptyWhenOverlayDoesNotContainArtifact() {
        Map<String, String> sources = new ConcurrentHashMap<>();

        Optional<CachedArtifact> artifact = materializer(sources).materializePom(
                List.of(RepositoryOverlay.mavenLocal(tempDir.resolve("missing-m2"))),
                coordinate("com.example", "app", "1.0.0"));

        assertTrue(artifact.isEmpty());
        assertEquals(Map.of(), sources);
    }

    private LocalOverlayMaterializer materializer(Map<String, String> sources) {
        return new LocalOverlayMaterializer(new LocalArtifactCache(tempDir.resolve("cache")), sources);
    }

    private Path writeMavenLocal(String repositoryPath, byte[] bytes) throws IOException {
        Path root = tempDir.resolve("m2");
        Path path = root.resolve(repositoryPath);
        Files.createDirectories(path.getParent());
        Files.write(path, bytes);
        return root;
    }

    private static Coordinate coordinate(String groupId, String artifactId, String version) {
        return new Coordinate(groupId, artifactId, Optional.of(version));
    }
}
