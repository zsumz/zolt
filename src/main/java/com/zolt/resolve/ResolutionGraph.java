package com.zolt.resolve;

import java.util.List;

public record ResolutionGraph(
        List<PackageNode> nodes,
        List<ResolutionEdge> edges,
        List<VersionConflict> conflicts,
        List<DependencyPolicyEffect> policyEffects) {
    public ResolutionGraph(
            List<PackageNode> nodes,
            List<ResolutionEdge> edges,
            List<VersionConflict> conflicts) {
        this(nodes, edges, conflicts, List.of());
    }

    public ResolutionGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        conflicts = List.copyOf(conflicts);
        policyEffects = policyEffects == null ? List.of() : List.copyOf(policyEffects);
    }
}
