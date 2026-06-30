package com.zolt.resolve.graph;

import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.traversal.DependencyTraversalDecision;

public record ResolutionEdge(
        PackageNode from,
        PackageNode to,
        DependencyRequest request,
        DependencyTraversalDecision traversalDecision) {
}
