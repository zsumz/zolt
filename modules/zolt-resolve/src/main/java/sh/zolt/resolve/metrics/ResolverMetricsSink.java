package sh.zolt.resolve.metrics;

public interface ResolverMetricsSink {
    void addGraphTraversalNanos(long nanos);

    void addVersionSelectionNanos(long nanos);
}
