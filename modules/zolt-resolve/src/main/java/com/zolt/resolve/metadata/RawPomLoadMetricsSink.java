package com.zolt.resolve.metadata;

public interface RawPomLoadMetricsSink {
    void recordRawPomCacheHit();

    void recordRawPomCacheMiss();

    void recordRawPomParse(long elapsedNanos);
}
