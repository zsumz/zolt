package sh.zolt.resolve.selection;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.graph.ResolutionEdge;
import sh.zolt.resolve.graph.ResolutionGraph;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.version.VersionSelectionResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class SelectedDependencyScopes {
    private SelectedDependencyScopes() {
    }

    public static Map<PackageNode, List<SelectedDependencyScope>> from(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageNode, List<SelectedDependencyScope>> requests = new LinkedHashMap<>();
        List<DependencyRequest> allRequests = new ArrayList<>();
        allRequests.addAll(directRequests);
        allRequests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        for (PackageNode node : selection.selectedNodes()) {
            Map<DependencyScope, SelectedDependencyScope> scopesByScope = new LinkedHashMap<>();
            allRequests.stream()
                    .filter(request -> request.packageId().equals(node.packageId()))
                    .filter(request -> request.artifactVariant().equals(node.variant()))
                    .forEach(request -> scopesByScope.merge(
                            request.scope(),
                            new SelectedDependencyScope(request.scope(), request.direct(), request.artifactDescriptor()),
                            SelectedDependencyScope::merge));
            List<SelectedDependencyScope> scopes = scopesByScope.values()
                    .stream()
                    .sorted(Comparator.comparing(selectedScope -> selectedScope.scope().lockfileName()))
                    .toList();
            if (!scopes.isEmpty()) {
                requests.put(node, scopes);
            }
        }
        return requests;
    }
}
