package com.zolt.resolve.materialization;

public interface ArtifactLoadMetricsSink {
    void recordPomCacheHit(long elapsedNanos);

    void recordPomDownload(long elapsedNanos);

    void recordJarCacheHit(long elapsedNanos);

    void recordJarDownload(long elapsedNanos);

    void recordArtifactCacheHit(long elapsedNanos);

    void recordArtifactDownload(long elapsedNanos);
}
