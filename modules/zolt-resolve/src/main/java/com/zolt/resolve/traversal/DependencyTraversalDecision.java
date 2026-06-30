package com.zolt.resolve.traversal;

public record DependencyTraversalDecision(
        boolean included,
        String reason) {
    public static DependencyTraversalDecision include(String reason) {
        return new DependencyTraversalDecision(true, reason);
    }

    public static DependencyTraversalDecision skip(String reason) {
        return new DependencyTraversalDecision(false, reason);
    }
}
