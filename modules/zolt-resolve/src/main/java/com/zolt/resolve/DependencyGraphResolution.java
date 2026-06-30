package com.zolt.resolve;

import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.version.VersionSelectionResult;

record DependencyGraphResolution(
        ResolutionGraph graph,
        VersionSelectionResult selection) {
}
