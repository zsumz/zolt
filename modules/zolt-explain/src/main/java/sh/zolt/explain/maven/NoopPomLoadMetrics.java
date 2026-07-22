package sh.zolt.explain.maven;

import sh.zolt.resolve.metrics.EffectivePomLoadMetricsSink;
import sh.zolt.resolve.metrics.RawPomLoadMetricsSink;

/**
 * A metrics sink that discards every measurement. The migration audit reuses the resolver's POM loaders
 * only to recover metadata; their timing/cache counters are irrelevant here.
 */
final class NoopPomLoadMetrics implements RawPomLoadMetricsSink, EffectivePomLoadMetricsSink {
    static final NoopPomLoadMetrics INSTANCE = new NoopPomLoadMetrics();

    private NoopPomLoadMetrics() {
    }

    @Override
    public void recordRawPomCacheHit() {
    }

    @Override
    public void recordRawPomCacheMiss() {
    }

    @Override
    public void recordRawPomParse(long elapsedNanos) {
    }

    @Override
    public void recordEffectivePomCacheHit() {
    }

    @Override
    public void recordEffectivePomCacheMiss() {
    }

    @Override
    public void recordEffectivePomBuild(long elapsedNanos) {
    }
}
