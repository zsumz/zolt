package sh.zolt.resolve.graph;

import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.traversal.DependencyTraversalDecision;

public record ResolutionEdge(
        PackageNode from,
        PackageNode to,
        DependencyRequest request,
        DependencyTraversalDecision traversalDecision) {
}
