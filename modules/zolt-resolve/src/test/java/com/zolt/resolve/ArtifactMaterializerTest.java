package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.RepositoryArtifact;
import java.net.URI;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArtifactMaterializerTest {
    private static final Coordinate APP = new Coordinate("com.example", "app", Optional.of("1.0.0"));

    @TempDir
    private Path tempDir;

    @Test
    void downloadsMissingJarThenReusesCachedJar() {
        ArtifactMaterializer materializer = materializer(ResolveOptions.defaults());
        RecordingMetrics metrics = new RecordingMetrics();
        AtomicInteger fetches = new AtomicInteger();

        CachedArtifact first = materializer.getJar(APP, coordinate -> {
            fetches.incrementAndGet();
            return artifact(coordinate, "com/example/app/1.0.0/app-1.0.0.jar", new byte[] {1, 2, 3});
        }, metrics);
        CachedArtifact second = materializer.getJar(APP, coordinate -> {
            fetches.incrementAndGet();
            return artifact(coordinate, "com/example/app/1.0.0/app-1.0.0.jar", new byte[] {4});
        }, metrics);

        assertArrayEquals(new byte[] {1, 2, 3}, first.bytes());
        assertArrayEquals(first.bytes(), second.bytes());
        assertEquals(1, fetches.get());
        assertEquals(1, metrics.jarDownloads);
        assertEquals(1, metrics.jarCacheHits);
    }

    @Test
    void usesCachedTypedArtifactInOfflineMode() {
        ArtifactDescriptor descriptor = new ArtifactDescriptor(APP, Optional.empty(), "properties");
        ArtifactMaterializer online = materializer(ResolveOptions.defaults());
        RecordingMetrics metrics = new RecordingMetrics();
        online.getArtifact(
                descriptor,
                ignored -> artifact(APP, "com/example/app/1.0.0/app-1.0.0.properties", new byte[] {9}),
                metrics);

        ArtifactMaterializer offline = materializer(ResolveOptions.offline(true));
        CachedArtifact artifact = offline.getArtifact(
                descriptor,
                ignored -> {
                    throw new AssertionError("offline materializer should not fetch");
                },
                metrics);

        assertArrayEquals(new byte[] {9}, artifact.bytes());
        assertEquals(1, metrics.artifactDownloads);
        assertEquals(1, metrics.artifactCacheHits);
    }

    private ArtifactMaterializer materializer(ResolveOptions options) {
        LocalArtifactCache cache = new LocalArtifactCache(tempDir.resolve("cache"));
        return new ArtifactMaterializer(
                cache,
                options,
                new LocalOverlayMaterializer(cache, new ConcurrentHashMap<>()));
    }

    private static RepositoryArtifact artifact(Coordinate coordinate, String path, byte[] bytes) {
        return new RepositoryArtifact(coordinate, path, URI.create("https://repo.example/" + path), bytes);
    }

    private static final class RecordingMetrics implements ArtifactLoadMetricsSink {
        private int jarCacheHits;
        private int jarDownloads;
        private int artifactCacheHits;
        private int artifactDownloads;

        @Override
        public void recordPomCacheHit(long elapsedNanos) {
        }

        @Override
        public void recordPomDownload(long elapsedNanos) {
        }

        @Override
        public void recordJarCacheHit(long elapsedNanos) {
            jarCacheHits++;
        }

        @Override
        public void recordJarDownload(long elapsedNanos) {
            jarDownloads++;
        }

        @Override
        public void recordArtifactCacheHit(long elapsedNanos) {
            artifactCacheHits++;
        }

        @Override
        public void recordArtifactDownload(long elapsedNanos) {
            artifactDownloads++;
        }
    }
}
