package com.zolt.resolve.traversal;

import com.zolt.resolve.graph.PackageNode;

record DependencyTraversalCandidate(
        DependencyTraversalItem item,
        PackageNode source,
        NormalizedDependency dependency) {
    DependencyTraversalCandidate {
        if (item == null) {
            throw new GraphTraversalException("Dependency traversal item is required.");
        }
        if (source == null) {
            throw new GraphTraversalException("Dependency traversal source node is required.");
        }
        if (dependency == null) {
            throw new GraphTraversalException("Dependency traversal candidate is required.");
        }
    }
}
