package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import java.util.List;
import java.util.Optional;

record DependencyTraversalItem(
        Optional<PackageNode> parent,
        DependencyRequest request,
        List<DependencyExclusion> edgeExclusions,
        DependencyTraversalDecision decision) {
    DependencyTraversalItem {
        parent = parent == null ? Optional.empty() : parent;
        edgeExclusions = List.copyOf(edgeExclusions);
    }

    static DependencyTraversalItem direct(DependencyRequest request) {
        return new DependencyTraversalItem(
                Optional.empty(),
                request,
                request.exclusions(),
                DependencyTraversalDecision.include("direct dependency"));
    }

    static DependencyTraversalItem transitive(
            PackageNode parent,
            DependencyRequest request,
            List<DependencyExclusion> edgeExclusions,
            DependencyTraversalDecision decision) {
        return new DependencyTraversalItem(Optional.of(parent), request, edgeExclusions, decision);
    }

    List<DependencyExclusion> matchingExclusions(Coordinate coordinate) {
        return edgeExclusions.stream()
                .filter(exclusion -> exclusion.matches(coordinate))
                .toList();
    }
}
