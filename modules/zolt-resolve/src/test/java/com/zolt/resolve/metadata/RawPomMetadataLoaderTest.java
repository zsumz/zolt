package com.zolt.resolve.metadata;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cache.CachedArtifact;
import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class RawPomMetadataLoaderTest {
    private static final Coordinate APP = new Coordinate("com.example", "app", Optional.of("1.0.0"));

    private final RawPomMetadataLoader loader = new RawPomMetadataLoader(new RawPomParser());

    @Test
    void parsesAndCachesRawPomByCoordinate() {
        AtomicInteger artifactLoads = new AtomicInteger();
        RecordingMetrics metrics = new RecordingMetrics();

        RawPom first = loader.load(APP, coordinate -> {
            artifactLoads.incrementAndGet();
            return cachedPom(coordinate, "app");
        }, metrics);
        RawPom second = loader.load(APP, coordinate -> {
            artifactLoads.incrementAndGet();
            return cachedPom(coordinate, "app");
        }, metrics);

        assertSame(first, second);
        assertEquals("app", first.artifactId());
        assertEquals(1, artifactLoads.get());
        assertEquals(1, metrics.cacheHits);
        assertEquals(1, metrics.cacheMisses);
        assertTrue(metrics.parseNanos > 0);
    }

    @Test
    void recordsMissAgainWhenParseFails() {
        RecordingMetrics metrics = new RecordingMetrics();

        org.junit.jupiter.api.Assertions.assertThrows(
                RuntimeException.class,
                () -> loader.load(APP, coordinate -> cachedPom(coordinate, "<not-xml"), metrics));
        RawPom parsed = loader.load(APP, coordinate -> cachedPom(coordinate, "app"), metrics);

        assertEquals("app", parsed.artifactId());
        assertEquals(0, metrics.cacheHits);
        assertEquals(2, metrics.cacheMisses);
        assertTrue(metrics.parseNanos > 0);
    }

    private static CachedArtifact cachedPom(Coordinate coordinate, String artifactId) {
        String xml = artifactId.startsWith("<")
                ? artifactId
                : """
                        <project>
                          <modelVersion>4.0.0</modelVersion>
                          <groupId>%s</groupId>
                          <artifactId>%s</artifactId>
                          <version>%s</version>
                        </project>
                        """.formatted(
                        coordinate.groupId(),
                        artifactId,
                        coordinate.version().orElseThrow());
        return new CachedArtifact(
                coordinate,
                "com/example/app/1.0.0/app-1.0.0.pom",
                Path.of("app-1.0.0.pom"),
                xml.getBytes(StandardCharsets.UTF_8));
    }

    private static final class RecordingMetrics implements RawPomLoadMetricsSink {
        private int cacheHits;
        private int cacheMisses;
        private long parseNanos;

        @Override
        public void recordRawPomCacheHit() {
            cacheHits++;
        }

        @Override
        public void recordRawPomCacheMiss() {
            cacheMisses++;
        }

        @Override
        public void recordRawPomParse(long elapsedNanos) {
            parseNanos += elapsedNanos;
        }
    }
}
