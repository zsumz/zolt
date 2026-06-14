package com.zolt.resolve;

interface ResolverMetricsSink {
    void addGraphTraversalNanos(long nanos);

    void addVersionSelectionNanos(long nanos);
}
