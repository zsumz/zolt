package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

final class ResolverMetricsCollectorTest {
    @Test
    void aggregatesResolverArtifactAndPomMetrics() {
        ResolverMetricsCollector collector = new ResolverMetricsCollector();

        collector.recordPomCacheHit(10L);
        collector.recordPomDownload(20L);
        collector.recordJarCacheHit(30L);
        collector.recordJarDownload(40L);
        collector.recordArtifactCacheHit(50L);
        collector.recordArtifactDownload(60L);
        collector.recordRawPomCacheHit();
        collector.recordRawPomCacheMiss();
        collector.recordRawPomParse(70L);
        collector.recordEffectivePomCacheHit();
        collector.recordEffectivePomCacheMiss();
        collector.recordEffectivePomBuild(80L);
        collector.addGraphTraversalNanos(90L);
        collector.addVersionSelectionNanos(100L);
        collector.addLockfileAssemblyNanos(110L);

        ResolveMetrics metrics = collector.metrics();
        assertEquals(3, collector.downloadCount());
        assertEquals(1, metrics.pomCacheHits());
        assertEquals(1, metrics.pomCacheMisses());
        assertEquals(1, metrics.jarCacheHits());
        assertEquals(1, metrics.jarCacheMisses());
        assertEquals(1, metrics.artifactCacheHits());
        assertEquals(1, metrics.artifactCacheMisses());
        assertEquals(1, metrics.rawPomCacheHits());
        assertEquals(1, metrics.rawPomCacheMisses());
        assertEquals(1, metrics.effectivePomCacheHits());
        assertEquals(1, metrics.effectivePomCacheMisses());
        assertEquals(10L, metrics.pomCacheHitNanos());
        assertEquals(20L, metrics.pomDownloadNanos());
        assertEquals(30L, metrics.jarCacheHitNanos());
        assertEquals(40L, metrics.jarDownloadNanos());
        assertEquals(50L, metrics.artifactCacheHitNanos());
        assertEquals(60L, metrics.artifactDownloadNanos());
        assertEquals(70L, metrics.rawPomParseNanos());
        assertEquals(80L, metrics.effectivePomBuildNanos());
        assertEquals(90L, metrics.graphTraversalNanos());
        assertEquals(100L, metrics.versionSelectionNanos());
        assertEquals(110L, metrics.lockfileAssemblyNanos());
    }
}
