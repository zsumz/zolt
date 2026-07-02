package sh.zolt.resolve.version;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.PackageId;
import sh.zolt.dependency.VersionComparator;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.graph.ResolutionEdge;
import sh.zolt.resolve.graph.ResolutionGraph;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class VersionSelector {
    private final VersionComparator versionComparator;

    public VersionSelector() {
        this(new VersionComparator());
    }

    VersionSelector(VersionComparator versionComparator) {
        this.versionComparator = versionComparator;
    }

    public VersionSelectionResult select(List<DependencyRequest> directRequests, ResolutionGraph graph) {
        Map<PackageId, List<DependencyRequest>> requestsByPackage = new LinkedHashMap<>();
        List<DependencyRequest> requests = new ArrayList<>();
        requests.addAll(directRequests);
        requests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        requests.stream()
                .sorted(Comparator.comparing(VersionSelector::requestSortKey))
                .forEach(request -> requestsByPackage
                        .computeIfAbsent(request.packageId(), ignored -> new ArrayList<>())
                        .add(request));

        List<PackageNode> selectedNodes = new ArrayList<>();
        List<VersionConflict> conflicts = new ArrayList<>();
        for (Map.Entry<PackageId, List<DependencyRequest>> entry : requestsByPackage.entrySet()) {
            PackageId packageId = entry.getKey();
            List<DependencyRequest> packageRequests = entry.getValue();
            Selection selection = selectVersion(packageRequests);
            selectedNodes.add(new PackageNode(packageId, selection.version()));
            if (distinctVersionCount(packageRequests) > 1) {
                conflicts.add(new VersionConflict(
                        packageId,
                        packageRequests,
                        selection.version(),
                        selection.reason()));
            }
        }

        return new VersionSelectionResult(selectedNodes, conflicts);
    }

    private Selection selectVersion(List<DependencyRequest> requests) {
        List<DependencyRequest> directRequests = requests.stream()
                .filter(DependencyRequest::direct)
                .toList();
        if (!directRequests.isEmpty()) {
            return new Selection(newestVersion(directRequests), ConflictSelectionReason.DIRECT_DEPENDENCY);
        }
        return new Selection(newestVersion(requests), ConflictSelectionReason.NEWEST_VERSION);
    }

    private String newestVersion(List<DependencyRequest> requests) {
        return requests.stream()
                .map(DependencyRequest::requestedVersion)
                .max(versionComparator)
                .orElseThrow();
    }

    private static long distinctVersionCount(List<DependencyRequest> requests) {
        return requests.stream().map(DependencyRequest::requestedVersion).distinct().count();
    }

    private static String requestSortKey(DependencyRequest request) {
        int originRank = request.direct() ? 0 : 1;
        return request.packageId() + ":" + originRank + ":" + request.requestedVersion() + ":" + request.scope();
    }

    private record Selection(String version, ConflictSelectionReason reason) {
    }
}
