package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class SelectedDependencyScopes {
    private SelectedDependencyScopes() {
    }

    static Map<PackageId, List<SelectedDependencyScope>> from(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedDependencyScope>> requests = new LinkedHashMap<>();
        List<DependencyRequest> allRequests = new ArrayList<>();
        allRequests.addAll(directRequests);
        allRequests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        for (PackageNode node : selection.selectedNodes()) {
            Map<DependencyScope, SelectedDependencyScope> scopesByScope = new LinkedHashMap<>();
            allRequests.stream()
                    .filter(request -> request.packageId().equals(node.packageId()))
                    .forEach(request -> scopesByScope.merge(
                            request.scope(),
                            new SelectedDependencyScope(request.scope(), request.direct(), request.artifactDescriptor()),
                            SelectedDependencyScope::merge));
            List<SelectedDependencyScope> scopes = scopesByScope.values()
                    .stream()
                    .sorted(Comparator.comparing(selectedScope -> selectedScope.scope().lockfileName()))
                    .toList();
            if (!scopes.isEmpty()) {
                requests.put(node.packageId(), scopes);
            }
        }
        return requests;
    }
}
