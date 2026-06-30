package com.zolt.resolve.metrics;

public interface RawPomLoadMetricsSink {
    void recordRawPomCacheHit();

    void recordRawPomCacheMiss();

    void recordRawPomParse(long elapsedNanos);
}
