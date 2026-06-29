package com.zolt.resolve.metadata;

public interface EffectivePomLoadMetricsSink {
    void recordEffectivePomCacheHit();

    void recordEffectivePomCacheMiss();

    void recordEffectivePomBuild(long elapsedNanos);
}
