package com.zolt.resolve;

public interface ResolverMetricsSink {
    void addGraphTraversalNanos(long nanos);

    void addVersionSelectionNanos(long nanos);
}
