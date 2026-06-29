package com.zolt.resolve;

import com.zolt.resolve.materialization.ArtifactLoadMetricsSink;
import com.zolt.resolve.metadata.EffectivePomLoadMetricsSink;
import com.zolt.resolve.metadata.RawPomLoadMetricsSink;

public final class ResolverMetricsCollector
        implements ResolverMetricsSink, ArtifactLoadMetricsSink, RawPomLoadMetricsSink, EffectivePomLoadMetricsSink {
    private int downloadCount;
    private int pomCacheHits;
    private int pomCacheMisses;
    private int jarCacheHits;
    private int jarCacheMisses;
    private int artifactCacheHits;
    private int artifactCacheMisses;
    private int rawPomCacheHits;
    private int rawPomCacheMisses;
    private int effectivePomCacheHits;
    private int effectivePomCacheMisses;
    private long pomCacheHitNanos;
    private long pomDownloadNanos;
    private long jarCacheHitNanos;
    private long jarDownloadNanos;
    private long artifactCacheHitNanos;
    private long artifactDownloadNanos;
    private long rawPomParseNanos;
    private long effectivePomBuildNanos;
    private long graphTraversalNanos;
    private long versionSelectionNanos;
    private long lockfileAssemblyNanos;

    @Override
    public synchronized void recordPomCacheHit(long elapsedNanos) {
        pomCacheHits++;
        pomCacheHitNanos += elapsedNanos;
    }

    @Override
    public synchronized void recordPomDownload(long elapsedNanos) {
        pomCacheMisses++;
        pomDownloadNanos += elapsedNanos;
        downloadCount++;
    }

    @Override
    public synchronized void recordJarCacheHit(long elapsedNanos) {
        jarCacheHits++;
        jarCacheHitNanos += elapsedNanos;
    }

    @Override
    public synchronized void recordJarDownload(long elapsedNanos) {
        jarCacheMisses++;
        jarDownloadNanos += elapsedNanos;
        downloadCount++;
    }

    @Override
    public synchronized void recordArtifactCacheHit(long elapsedNanos) {
        artifactCacheHits++;
        artifactCacheHitNanos += elapsedNanos;
    }

    @Override
    public synchronized void recordArtifactDownload(long elapsedNanos) {
        artifactCacheMisses++;
        artifactDownloadNanos += elapsedNanos;
        downloadCount++;
    }

    @Override
    public synchronized void recordRawPomCacheHit() {
        rawPomCacheHits++;
    }

    @Override
    public synchronized void recordRawPomCacheMiss() {
        rawPomCacheMisses++;
    }

    @Override
    public synchronized void recordRawPomParse(long elapsedNanos) {
        rawPomParseNanos += elapsedNanos;
    }

    @Override
    public synchronized void recordEffectivePomCacheHit() {
        effectivePomCacheHits++;
    }

    @Override
    public synchronized void recordEffectivePomCacheMiss() {
        effectivePomCacheMisses++;
    }

    @Override
    public synchronized void recordEffectivePomBuild(long elapsedNanos) {
        effectivePomBuildNanos += elapsedNanos;
    }

    @Override
    public synchronized void addGraphTraversalNanos(long nanos) {
        graphTraversalNanos += nanos;
    }

    @Override
    public synchronized void addVersionSelectionNanos(long nanos) {
        versionSelectionNanos += nanos;
    }

    public synchronized void addLockfileAssemblyNanos(long nanos) {
        lockfileAssemblyNanos += nanos;
    }

    public synchronized int downloadCount() {
        return downloadCount;
    }

    public synchronized ResolveMetrics metrics() {
        return new ResolveMetrics(
                pomCacheHits,
                pomCacheMisses,
                jarCacheHits,
                jarCacheMisses,
                artifactCacheHits,
                artifactCacheMisses,
                rawPomCacheHits,
                rawPomCacheMisses,
                effectivePomCacheHits,
                effectivePomCacheMisses,
                pomCacheHitNanos,
                pomDownloadNanos,
                jarCacheHitNanos,
                jarDownloadNanos,
                artifactCacheHitNanos,
                artifactDownloadNanos,
                rawPomParseNanos,
                effectivePomBuildNanos,
                graphTraversalNanos,
                versionSelectionNanos,
                lockfileAssemblyNanos,
                0L,
                0L);
    }
}
