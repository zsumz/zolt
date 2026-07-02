package sh.zolt.resolve;

import sh.zolt.resolve.graph.ResolutionGraph;
import sh.zolt.resolve.version.VersionSelectionResult;

record DependencyGraphResolution(
        ResolutionGraph graph,
        VersionSelectionResult selection) {
}
