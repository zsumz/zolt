package com.zolt.resolve;

interface EffectivePomLoadMetricsSink {
    void recordEffectivePomCacheHit();

    void recordEffectivePomCacheMiss();

    void recordEffectivePomBuild(long elapsedNanos);
}
