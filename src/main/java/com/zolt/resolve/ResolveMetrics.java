package com.zolt.resolve;

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
        int effectivePomCacheMisses) {
    public static ResolveMetrics empty() {
        return new ResolveMetrics(0, 0, 0, 0, 0, 0, 0, 0, 0, 0);
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
                effectivePomCacheMisses + other.effectivePomCacheMisses);
    }
}
