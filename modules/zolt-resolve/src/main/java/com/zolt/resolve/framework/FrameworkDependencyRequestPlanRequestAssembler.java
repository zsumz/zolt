package com.zolt.resolve.framework;

import com.zolt.dependency.PackageId;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.graph.PackageNode;
import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.selection.SelectedDependencyScope;
import com.zolt.resolve.selection.SelectedDependencyScopes;
import com.zolt.resolve.version.VersionSelectionResult;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

public final class FrameworkDependencyRequestPlanRequestAssembler {
    public FrameworkDependencyRequestPlanRequest assemble(
            ProjectConfig config,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests,
            Map<PackageId, String> managedVersions,
            FrameworkDependencyRequestPlanRequest.ArtifactPathResolver artifactPathResolver,
            Supplier<List<DependencyRequest>> platformPropertiesRequests) {
        return new FrameworkDependencyRequestPlanRequest(
                config,
                candidates(graph, selection, directRequests),
                selectedVersions(selection),
                managedVersions,
                artifactPathResolver,
                platformPropertiesRequests);
    }

    private static List<FrameworkDependencyCandidate> candidates(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedDependencyScope>> selectedScopes = SelectedDependencyScopes.from(
                graph,
                selection,
                directRequests);
        return selection.selectedNodes().stream()
                .sorted(Comparator.comparing(node -> node.packageId() + ":" + node.selectedVersion()))
                .map(node -> new FrameworkDependencyCandidate(
                        node.packageId(),
                        node.selectedVersion(),
                        selectedScopes.getOrDefault(node.packageId(), List.of()).stream()
                                .map(SelectedDependencyScope::scope)
                                .toList()))
                .toList();
    }

    private static Map<PackageId, String> selectedVersions(VersionSelectionResult selection) {
        Map<PackageId, String> versions = new LinkedHashMap<>();
        for (PackageNode node : selection.selectedNodes()) {
            versions.put(node.packageId(), node.selectedVersion());
        }
        return versions;
    }
}
