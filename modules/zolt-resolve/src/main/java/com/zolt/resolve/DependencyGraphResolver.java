package com.zolt.resolve;

import com.zolt.dependency.PackageId;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.metrics.ResolverMetricsSink;
import com.zolt.resolve.metadata.DependencyMetadataSource;
import com.zolt.resolve.metadata.platform.ManagedVersion;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.traversal.DependencyGraphTraverser;
import com.zolt.resolve.version.VersionSelectionResult;
import com.zolt.resolve.version.VersionSelector;
import java.util.List;
import java.util.Map;

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
            Map<PackageId, ManagedVersion> managedVersions,
            List<DependencyRequest> requests,
            ResolverMetricsSink metrics) {
        DependencyGraphTraverser traverser = graphTraverserFactory.create(
                metadataSource,
                dependencyPolicy,
                managedVersions);
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
