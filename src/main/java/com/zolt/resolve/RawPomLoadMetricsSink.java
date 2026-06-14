package com.zolt.resolve;

interface RawPomLoadMetricsSink {
    void recordRawPomCacheHit();

    void recordRawPomCacheMiss();

    void recordRawPomParse(long elapsedNanos);
}
