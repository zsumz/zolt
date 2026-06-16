package com.zolt.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.RepositoryArtifact;
import com.zolt.maven.RepositoryClientException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LocalArtifactCacheTest {
    private final CoordinateParser parser = new CoordinateParser();

    @TempDir
    private Path tempDir;

    @Test
    void cachePathsAreDeterministic() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        assertEquals(
                tempDir.resolve("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"),
                cache.pomPath(coordinate));
        assertEquals(
                tempDir.resolve("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"),
                cache.jarPath(coordinate));
    }

    @Test
    void repeatedPomFetchUsesCachedArtifact() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger fetchCount = new AtomicInteger();
        ArtifactFetcher fetcher = requested -> {
            fetchCount.incrementAndGet();
            return artifact(requested, "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", "<project/>");
        };

        CachedArtifact first = cache.getOrFetchPom(coordinate, fetcher);
        CachedArtifact second = cache.getOrFetchPom(coordinate, fetcher);

        assertEquals(1, fetchCount.get());
        assertArrayEquals(first.bytes(), second.bytes());
        assertEquals(first.cachePath(), second.cachePath());
    }

    @Test
    void repeatedJarFetchUsesCachedArtifact() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        AtomicInteger fetchCount = new AtomicInteger();
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};

        CachedArtifact first = cache.getOrFetchJar(coordinate, requested -> {
            fetchCount.incrementAndGet();
            return new RepositoryArtifact(
                    requested,
                    "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar",
                    URI.create("https://repo.example/guava.jar"),
                    jarBytes);
        });
        CachedArtifact second = cache.getOrFetchJar(coordinate, requested -> {
            throw new AssertionError("cache should avoid the second fetch");
        });

        assertEquals(1, fetchCount.get());
        assertArrayEquals(first.bytes(), second.bytes());
    }

    @Test
    void repeatedClassifierArtifactFetchUsesCachedArtifact() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("io.quarkus:quarkus-custom-deployment:1.0.0");
        ArtifactDescriptor descriptor = ArtifactDescriptor.jar(coordinate, Optional.of("deployment"));
        AtomicInteger fetchCount = new AtomicInteger();
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};

        CachedArtifact first = cache.getOrFetchArtifact(descriptor, requested -> {
            fetchCount.incrementAndGet();
            return new RepositoryArtifact(
                    requested,
                    "io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                    URI.create("https://repo.example/quarkus-custom-deployment-1.0.0-deployment.jar"),
                    jarBytes);
        });
        CachedArtifact second = cache.getOrFetchArtifact(descriptor, requested -> {
            throw new AssertionError("cache should avoid the second fetch");
        });

        assertEquals(1, fetchCount.get());
        assertEquals(
                tempDir.resolve("io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar"),
                first.cachePath());
        assertArrayEquals(first.bytes(), second.bytes());
    }

    @Test
    void cachedOnlyPomDoesNotFetch() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        cache.getOrFetchPom(coordinate, requested ->
                artifact(requested, "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", "<project/>"));

        CachedArtifact artifact = cache.getCachedPom(coordinate);

        assertEquals(cache.pomPath(coordinate), artifact.cachePath());
        assertArrayEquals("<project/>".getBytes(StandardCharsets.UTF_8), artifact.bytes());
    }

    @Test
    void cachedOnlyJarFailsClearlyWhenMissing() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        ArtifactCacheException exception = assertThrows(
                ArtifactCacheException.class,
                () -> cache.getCachedJar(coordinate));

        assertTrue(exception.getMessage().contains("Offline mode requires cached JAR"));
        assertTrue(exception.getMessage().contains("Run the command without --offline"));
    }

    @Test
    void failedDownloadDoesNotCreateCacheFile() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        assertThrows(
                RepositoryClientException.class,
                () -> cache.getOrFetchPom(coordinate, requested -> {
                    throw new RepositoryClientException("network failed");
                }));

        assertFalse(Files.exists(cache.pomPath(coordinate)));
    }

    @Test
    void emptyCachedArtifactIsNotValid() throws Exception {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        Files.createDirectories(cache.pomPath(coordinate).getParent());
        Files.write(cache.pomPath(coordinate), new byte[0]);

        ArtifactCacheException exception = assertThrows(
                ArtifactCacheException.class,
                () -> cache.getOrFetchPom(coordinate, requested -> artifact(requested, "unused", "<project/>")));

        assertTrue(exception.getMessage().contains("is empty"));
        assertTrue(exception.getMessage().contains("Delete it and run the command again."));
    }

    @Test
    void emptyDownloadDoesNotUpdateCache() {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir);
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        ArtifactCacheException exception = assertThrows(
                ArtifactCacheException.class,
                () -> cache.getOrFetchPom(coordinate, requested -> new RepositoryArtifact(
                        requested,
                        "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom",
                        URI.create("https://repo.example/guava.pom"),
                        new byte[0])));

        assertEquals("Downloaded artifact com.google.guava:guava:33.4.0-jre is empty. The cache was not updated.", exception.getMessage());
        assertFalse(Files.exists(cache.pomPath(coordinate)));
    }

    private static RepositoryArtifact artifact(Coordinate coordinate, String path, String body) {
        return new RepositoryArtifact(
                coordinate,
                path,
                URI.create("https://repo.example/" + path),
                body.getBytes(StandardCharsets.UTF_8));
    }
}
