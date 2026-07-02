package sh.zolt.resolve.version;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.request.RequestOrigin;
import sh.zolt.resolve.graph.ResolutionEdge;
import sh.zolt.resolve.graph.ResolutionGraph;
import sh.zolt.resolve.traversal.DependencyTraversalDecision;
import java.util.List;
import org.junit.jupiter.api.Test;

final class VersionSelectorTest {
    private final VersionSelector selector = new VersionSelector();

    @Test
    void directDependencyWinsOverTransitiveDependency() {
        PackageId packageId = new PackageId("org.slf4j", "slf4j-api");
        ResolutionGraph graph = graphWithTransitiveRequests(List.of(
                transitive(packageId, "2.0.16")));

        VersionSelectionResult result = selector.select(List.of(direct(packageId, "2.0.15")), graph);

        assertEquals(List.of(new PackageNode(packageId, "2.0.15")), result.selectedNodes());
        VersionConflict conflict = result.conflicts().getFirst();
        assertEquals("2.0.15", conflict.selectedVersion());
        assertEquals(ConflictSelectionReason.DIRECT_DEPENDENCY, conflict.selectionReason());
    }

    @Test
    void otherwiseNewestVersionWins() {
        PackageId packageId = new PackageId("org.slf4j", "slf4j-api");
        ResolutionGraph graph = graphWithTransitiveRequests(List.of(
                transitive(packageId, "2.0.15"),
                transitive(packageId, "2.0.16")));

        VersionSelectionResult result = selector.select(List.of(), graph);

        assertEquals(List.of(new PackageNode(packageId, "2.0.16")), result.selectedNodes());
        VersionConflict conflict = result.conflicts().getFirst();
        assertEquals("2.0.16", conflict.selectedVersion());
        assertEquals(ConflictSelectionReason.NEWEST_VERSION, conflict.selectionReason());
    }

    @Test
    void conflictsAreRecordedNotHidden() {
        PackageId packageId = new PackageId("com.google.guava", "guava");
        ResolutionGraph graph = graphWithTransitiveRequests(List.of(
                transitive(packageId, "33.3.1-jre"),
                transitive(packageId, "33.4.0-jre")));

        VersionSelectionResult result = selector.select(List.of(), graph);

        assertEquals(1, result.conflicts().size());
        VersionConflict conflict = result.conflicts().getFirst();
        assertEquals(packageId, conflict.packageId());
        assertEquals(List.of("33.3.1-jre", "33.4.0-jre"), conflict.requests().stream().map(DependencyRequest::requestedVersion).toList());
    }

    @Test
    void noConflictWhenAllRequestsUseSameVersion() {
        PackageId packageId = new PackageId("org.slf4j", "slf4j-api");
        ResolutionGraph graph = graphWithTransitiveRequests(List.of(
                transitive(packageId, "2.0.16"),
                transitive(packageId, "2.0.16")));

        VersionSelectionResult result = selector.select(List.of(), graph);

        assertEquals(List.of(new PackageNode(packageId, "2.0.16")), result.selectedNodes());
        assertTrue(result.conflicts().isEmpty());
    }

    @Test
    void sameInputProducesSameSelectionRegardlessOfOrder() {
        PackageId slf4j = new PackageId("org.slf4j", "slf4j-api");
        PackageId guava = new PackageId("com.google.guava", "guava");
        ResolutionGraph leftGraph = graphWithTransitiveRequests(List.of(
                transitive(slf4j, "2.0.15"),
                transitive(guava, "33.4.0-jre"),
                transitive(slf4j, "2.0.16")));
        ResolutionGraph rightGraph = graphWithTransitiveRequests(List.of(
                transitive(slf4j, "2.0.16"),
                transitive(slf4j, "2.0.15"),
                transitive(guava, "33.4.0-jre")));

        VersionSelectionResult left = selector.select(List.of(), leftGraph);
        VersionSelectionResult right = selector.select(List.of(), rightGraph);

        assertEquals(left.selectedNodes(), right.selectedNodes());
        assertEquals(left.conflicts(), right.conflicts());
    }

    @Test
    void selectedNodesAreSortedDeterministically() {
        PackageId slf4j = new PackageId("org.slf4j", "slf4j-api");
        PackageId guava = new PackageId("com.google.guava", "guava");
        ResolutionGraph graph = graphWithTransitiveRequests(List.of(
                transitive(slf4j, "2.0.16"),
                transitive(guava, "33.4.0-jre")));

        VersionSelectionResult result = selector.select(List.of(), graph);

        assertEquals(List.of(
                new PackageNode(guava, "33.4.0-jre"),
                new PackageNode(slf4j, "2.0.16")), result.selectedNodes());
    }

    private static ResolutionGraph graphWithTransitiveRequests(List<DependencyRequest> requests) {
        PackageNode root = new PackageNode(new PackageId("com.example", "root"), "1.0.0");
        List<ResolutionEdge> edges = requests.stream()
                .map(request -> new ResolutionEdge(
                        root,
                        new PackageNode(request.packageId(), request.requestedVersion()),
                        request,
                        DependencyTraversalDecision.include("test")))
                .toList();
        return new ResolutionGraph(List.of(), edges, List.of());
    }

    private static DependencyRequest direct(PackageId packageId, String version) {
        return new DependencyRequest(packageId, version, DependencyScope.COMPILE, RequestOrigin.DIRECT);
    }

    private static DependencyRequest transitive(PackageId packageId, String version) {
        return new DependencyRequest(packageId, version, DependencyScope.COMPILE, RequestOrigin.TRANSITIVE);
    }
}
