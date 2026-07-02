package sh.zolt.resolve.metrics;

public interface EffectivePomLoadMetricsSink {
    void recordEffectivePomCacheHit();

    void recordEffectivePomCacheMiss();

    void recordEffectivePomBuild(long elapsedNanos);
}
