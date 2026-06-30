package com.zolt.resolve.metadata.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.EffectiveRawPom;
import com.zolt.maven.repository.RawPom;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.maven.repository.RawPomParent;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.metrics.EffectivePomLoadMetricsSink;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class EffectivePomMetadataLoaderTest {
    private static final Coordinate APP = new Coordinate("com.example", "app", Optional.of("1.0.0"));

    private final EffectivePomMetadataLoader loader = new EffectivePomMetadataLoader(
            new ParentPomChainLoader(),
            new EffectivePomInheritanceBuilder(),
            new ImportedBomDependencyManagementExpander());

    @Test
    void buildsCachesAndRecordsEffectivePomMetrics() {
        RawPom app = pom("app", Optional.empty(), Map.of("app", "value"), List.of());
        AtomicInteger rawLoads = new AtomicInteger();
        RecordingMetrics metrics = new RecordingMetrics();

        EffectiveRawPom first = loader.load(APP, List.of(), coordinate -> {
            rawLoads.incrementAndGet();
            return app;
        }, metrics);
        EffectiveRawPom second = loader.load(APP, List.of(), coordinate -> {
            rawLoads.incrementAndGet();
            return app;
        }, metrics);

        assertSame(first, second);
        assertEquals("com.example", first.groupId());
        assertEquals("1.0.0", first.version());
        assertEquals(1, rawLoads.get());
        assertEquals(1, metrics.cacheHits);
        assertEquals(1, metrics.cacheMisses);
        assertTrue(metrics.buildNanos > 0);
    }

    @Test
    void loadsParentChainBeforeInheritance() {
        RawPom app = pom("app", parent("com.example", "parent", "1.0.0"), Map.of("app", "value"), List.of());
        RawPom parent = pom("parent", Optional.empty(), Map.of("parent", "value"), List.of());
        RecordingMetrics metrics = new RecordingMetrics();

        EffectiveRawPom effective = loader.load(APP, List.of(), coordinate -> {
            if (coordinate.artifactId().equals("parent")) {
                return parent;
            }
            return app;
        }, metrics);

        assertEquals(List.of(parent), effective.parents());
        assertEquals(Map.of("parent", "value", "app", "value"), effective.properties());
    }

    @Test
    void detectsImportedBomCyclesBeforeStartingLoad() {
        RecordingMetrics metrics = new RecordingMetrics();

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> loader.load(
                        APP,
                        List.of(APP.toString()),
                        coordinate -> {
                            throw new AssertionError("unexpected raw POM load");
                        },
                        metrics));

        assertTrue(exception.getMessage().contains("Imported BOM cycle detected"));
        assertEquals(0, metrics.cacheHits);
        assertEquals(0, metrics.cacheMisses);
        assertEquals(0, metrics.buildNanos);
    }

    private static RawPom pom(
            String artifactId,
            Optional<RawPomParent> parent,
            Map<String, String> properties,
            List<RawPomDependency> dependencyManagement) {
        return new RawPom(
                Optional.of("com.example"),
                artifactId,
                Optional.of("1.0.0"),
                "pom",
                parent,
                Optional.empty(),
                properties,
                dependencyManagement,
                List.of());
    }

    private static Optional<RawPomParent> parent(String groupId, String artifactId, String version) {
        return Optional.of(new RawPomParent(groupId, artifactId, version, Optional.empty()));
    }

    private static final class RecordingMetrics implements EffectivePomLoadMetricsSink {
        private int cacheHits;
        private int cacheMisses;
        private long buildNanos;

        @Override
        public void recordEffectivePomCacheHit() {
            cacheHits++;
        }

        @Override
        public void recordEffectivePomCacheMiss() {
            cacheMisses++;
        }

        @Override
        public void recordEffectivePomBuild(long elapsedNanos) {
            buildNanos += elapsedNanos;
        }
    }
}
