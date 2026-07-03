package sh.zolt.resolve.metrics;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ResolveMetricsTest {
    @Test
    void plusAggregatesCountersAndTimings() {
        ResolveMetrics left = metrics(1, 10L);
        ResolveMetrics right = metrics(2, 20L);

        ResolveMetrics total = left.plus(right);

        assertEquals(3, total.pomCacheHits());
        assertEquals(5, total.pomCacheMisses());
        assertEquals(7, total.jarCacheHits());
        assertEquals(9, total.jarCacheMisses());
        assertEquals(11, total.artifactCacheHits());
        assertEquals(13, total.artifactCacheMisses());
        assertEquals(15, total.rawPomCacheHits());
        assertEquals(17, total.rawPomCacheMisses());
        assertEquals(19, total.effectivePomCacheHits());
        assertEquals(21, total.effectivePomCacheMisses());
        assertEquals(30L, total.pomCacheHitNanos());
        assertEquals(32L, total.pomDownloadNanos());
        assertEquals(34L, total.jarCacheHitNanos());
        assertEquals(36L, total.jarDownloadNanos());
        assertEquals(38L, total.artifactCacheHitNanos());
        assertEquals(40L, total.artifactDownloadNanos());
        assertEquals(42L, total.rawPomParseNanos());
        assertEquals(44L, total.effectivePomBuildNanos());
        assertEquals(46L, total.graphTraversalNanos());
        assertEquals(48L, total.versionSelectionNanos());
        assertEquals(50L, total.lockfileAssemblyNanos());
        assertEquals(52L, total.lockfileWriteNanos());
        assertEquals(54L, total.lockfileVerificationNanos());
    }

    @Test
    void lockfileTimingHelpersAddOnlyTheirOwnFields() {
        ResolveMetrics metrics = ResolveMetrics.empty()
                .withLockfileWriteNanos(12L)
                .withLockfileVerificationNanos(34L)
                .withLockfileWriteNanos(56L);

        assertEquals(68L, metrics.lockfileWriteNanos());
        assertEquals(34L, metrics.lockfileVerificationNanos());
        assertEquals(0L, metrics.pomCacheHitNanos());
    }

    private static ResolveMetrics metrics(int baseCount, long baseNanos) {
        return new ResolveMetrics(
                baseCount,
                baseCount + 1,
                baseCount + 2,
                baseCount + 3,
                baseCount + 4,
                baseCount + 5,
                baseCount + 6,
                baseCount + 7,
                baseCount + 8,
                baseCount + 9,
                baseNanos,
                baseNanos + 1,
                baseNanos + 2,
                baseNanos + 3,
                baseNanos + 4,
                baseNanos + 5,
                baseNanos + 6,
                baseNanos + 7,
                baseNanos + 8,
                baseNanos + 9,
                baseNanos + 10,
                baseNanos + 11,
                baseNanos + 12);
    }
}
