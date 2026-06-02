package com.zolt.resolve;

import java.util.List;

public record ResolutionGraph(
        List<PackageNode> nodes,
        List<ResolutionEdge> edges,
        List<VersionConflict> conflicts) {
    public ResolutionGraph {
        nodes = List.copyOf(nodes);
        edges = List.copyOf(edges);
        conflicts = List.copyOf(conflicts);
    }
}
