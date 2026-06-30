package com.zolt.resolve.traversal;

public final class DependencyTraversalPolicy {
    public DependencyTraversalDecision decide(NormalizedDependency dependency, boolean directDependency) {
        if (directDependency) {
            return DependencyTraversalDecision.include("direct dependency");
        }
        if (dependency.optional()) {
            return DependencyTraversalDecision.skip("optional transitive dependency");
        }
        return DependencyTraversalDecision.include("non-optional transitive dependency");
    }
}
