package com.zolt.resolve.metrics;

public record ResolveMetrics(
        int pomCacheHits,
        int pomCacheMisses,
        int jarCacheHits,
        int jarCacheMisses,
        int artifactCacheHits,
        int artifactCacheMisses,
        int rawPomCacheHits,
        int rawPomCacheMisses,
        int effectivePomCacheHits,
        int effectivePomCacheMisses,
        long pomCacheHitNanos,
        long pomDownloadNanos,
        long jarCacheHitNanos,
        long jarDownloadNanos,
        long artifactCacheHitNanos,
        long artifactDownloadNanos,
        long rawPomParseNanos,
        long effectivePomBuildNanos,
        long graphTraversalNanos,
        long versionSelectionNanos,
        long lockfileAssemblyNanos,
        long lockfileWriteNanos,
        long lockfileVerificationNanos) {
    public static ResolveMetrics empty() {
        return new ResolveMetrics(
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L,
                0L);
    }

    public ResolveMetrics plus(ResolveMetrics other) {
        return new ResolveMetrics(
                pomCacheHits + other.pomCacheHits,
                pomCacheMisses + other.pomCacheMisses,
                jarCacheHits + other.jarCacheHits,
                jarCacheMisses + other.jarCacheMisses,
                artifactCacheHits + other.artifactCacheHits,
                artifactCacheMisses + other.artifactCacheMisses,
                rawPomCacheHits + other.rawPomCacheHits,
                rawPomCacheMisses + other.rawPomCacheMisses,
                effectivePomCacheHits + other.effectivePomCacheHits,
                effectivePomCacheMisses + other.effectivePomCacheMisses,
                pomCacheHitNanos + other.pomCacheHitNanos,
                pomDownloadNanos + other.pomDownloadNanos,
                jarCacheHitNanos + other.jarCacheHitNanos,
                jarDownloadNanos + other.jarDownloadNanos,
                artifactCacheHitNanos + other.artifactCacheHitNanos,
                artifactDownloadNanos + other.artifactDownloadNanos,
                rawPomParseNanos + other.rawPomParseNanos,
                effectivePomBuildNanos + other.effectivePomBuildNanos,
                graphTraversalNanos + other.graphTraversalNanos,
                versionSelectionNanos + other.versionSelectionNanos,
                lockfileAssemblyNanos + other.lockfileAssemblyNanos,
                lockfileWriteNanos + other.lockfileWriteNanos,
                lockfileVerificationNanos + other.lockfileVerificationNanos);
    }

    public ResolveMetrics withLockfileWriteNanos(long additionalNanos) {
        return withLockfileNanos(additionalNanos, 0L);
    }

    public ResolveMetrics withLockfileVerificationNanos(long additionalNanos) {
        return withLockfileNanos(0L, additionalNanos);
    }

    private ResolveMetrics withLockfileNanos(long writeNanos, long verificationNanos) {
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
                lockfileWriteNanos + writeNanos,
                lockfileVerificationNanos + verificationNanos);
    }
}
