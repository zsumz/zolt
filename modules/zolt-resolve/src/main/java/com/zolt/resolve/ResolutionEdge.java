package com.zolt.resolve;

public record ResolutionEdge(
        PackageNode from,
        PackageNode to,
        DependencyRequest request,
        DependencyTraversalDecision traversalDecision) {
}
