package com.zolt.resolve;

import com.zolt.project.DependencyPolicySettings;
import java.util.List;

final class DependencyGraphResolver {
    private final ResolveService.DependencyGraphTraverserFactory graphTraverserFactory;
    private final VersionSelector versionSelector;

    DependencyGraphResolver(
            ResolveService.DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector) {
        this.graphTraverserFactory = graphTraverserFactory;
        this.versionSelector = versionSelector;
    }

    DependencyGraphResolution resolve(
            DependencyMetadataSource metadataSource,
            DependencyPolicySettings dependencyPolicy,
            List<DependencyRequest> requests,
            ResolverMetricsSink metrics) {
        DependencyGraphTraverser traverser = graphTraverserFactory.create(metadataSource, dependencyPolicy);
        long traversalStarted = System.nanoTime();
        ResolutionGraph graph = traverser.traverse(requests);
        metrics.addGraphTraversalNanos(elapsedSince(traversalStarted));
        long selectionStarted = System.nanoTime();
        VersionSelectionResult selection = versionSelector.select(requests, graph);
        metrics.addVersionSelectionNanos(elapsedSince(selectionStarted));
        return new DependencyGraphResolution(graph, selection);
    }

    private static long elapsedSince(long started) {
        return System.nanoTime() - started;
    }
}
