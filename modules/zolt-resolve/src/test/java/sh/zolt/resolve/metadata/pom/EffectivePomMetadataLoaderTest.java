package sh.zolt.resolve.metadata.pom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.EffectiveRawPom;
import sh.zolt.maven.repository.RawPom;
import sh.zolt.maven.repository.RawPomDependency;
import sh.zolt.maven.repository.RawPomParent;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.metrics.EffectivePomLoadMetricsSink;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

    @Test
    void sharesInFlightEffectivePomLoadBetweenCallers() throws Exception {
        RawPom app = pom("app", Optional.empty(), Map.of("app", "value"), List.of());
        AtomicInteger rawLoads = new AtomicInteger();
        CountDownLatch cacheHitRecorded = new CountDownLatch(1);
        RecordingMetrics metrics = new RecordingMetrics(cacheHitRecorded);
        CountDownLatch loadStarted = new CountDownLatch(1);
        CountDownLatch releaseLoad = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<EffectiveRawPom> first = executor.submit(() -> loader.load(APP, List.of(), coordinate -> {
                rawLoads.incrementAndGet();
                loadStarted.countDown();
                await(releaseLoad);
                return app;
            }, metrics));
            assertTrue(loadStarted.await(5, TimeUnit.SECONDS));

            Future<EffectiveRawPom> second = executor.submit(() -> loader.load(APP, List.of(), coordinate -> {
                rawLoads.incrementAndGet();
                return app;
            }, metrics));
            assertTrue(cacheHitRecorded.await(5, TimeUnit.SECONDS));
            releaseLoad.countDown();

            EffectiveRawPom firstPom = first.get(5, TimeUnit.SECONDS);
            EffectiveRawPom secondPom = second.get(5, TimeUnit.SECONDS);
            assertSame(firstPom, secondPom);
            assertEquals("app", secondPom.rawPom().artifactId());
            assertEquals(1, rawLoads.get());
            assertEquals(1, metrics.cacheHits);
            assertEquals(1, metrics.cacheMisses);
            assertTrue(metrics.buildNanos > 0);
        } finally {
            releaseLoad.countDown();
            executor.shutdownNow();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError(exception);
        }
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
        private final CountDownLatch cacheHitRecorded;

        private RecordingMetrics() {
            this(new CountDownLatch(0));
        }

        private RecordingMetrics(CountDownLatch cacheHitRecorded) {
            this.cacheHitRecorded = cacheHitRecorded;
        }

        @Override
        public synchronized void recordEffectivePomCacheHit() {
            cacheHits++;
            cacheHitRecorded.countDown();
        }

        @Override
        public synchronized void recordEffectivePomCacheMiss() {
            cacheMisses++;
        }

        @Override
        public synchronized void recordEffectivePomBuild(long elapsedNanos) {
            buildNanos += elapsedNanos;
        }
    }
}
