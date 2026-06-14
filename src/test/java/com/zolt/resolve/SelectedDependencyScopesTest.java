package com.zolt.resolve;

import com.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class SelectedDependencyScopesTest {
    private static final PackageId APP = new PackageId("com.example", "app");
    private static final PackageId LIB = new PackageId("com.example", "lib");
    private static final PackageNode APP_NODE = new PackageNode(APP, "1.0.0");
    private static final PackageNode LIB_NODE = new PackageNode(LIB, "2.0.0");

    @Test
    void mergesDirectAndTransitiveRequestsForSelectedPackages() {
        DependencyRequest directCompile = new DependencyRequest(APP, "1.0.0", DependencyScope.COMPILE, RequestOrigin.DIRECT);
        DependencyRequest transitiveCompile = new DependencyRequest(APP, "1.0.0", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE);
        ResolutionGraph graph = new ResolutionGraph(
                List.of(APP_NODE, LIB_NODE),
                List.of(new ResolutionEdge(
                        LIB_NODE,
                        APP_NODE,
                        transitiveCompile,
                        DependencyTraversalDecision.include("test"))),
                List.of());

        Map<PackageId, List<SelectedDependencyScope>> scopes = SelectedDependencyScopes.from(
                graph,
                new VersionSelectionResult(List.of(APP_NODE, LIB_NODE), List.of()),
                List.of(directCompile));

        assertEquals(List.of(APP), scopes.keySet().stream().toList());
        assertEquals(DependencyScope.COMPILE, scopes.get(APP).getFirst().scope());
        assertTrue(scopes.get(APP).getFirst().direct());
    }

    @Test
    void preservesArtifactDescriptorAndSortsScopesByLockfileName() {
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                new Coordinate("org.jacoco", "org.jacoco.agent", Optional.of("0.8.14")),
                Optional.of("runtime"),
                "jar");
        DependencyRequest coverage = new DependencyRequest(
                APP,
                "0.8.14",
                DependencyScope.TOOL_COVERAGE,
                RequestOrigin.DIRECT,
                Optional.of(descriptor));
        DependencyRequest test = new DependencyRequest(APP, "1.0.0", DependencyScope.TEST, RequestOrigin.DIRECT);

        Map<PackageId, List<SelectedDependencyScope>> scopes = SelectedDependencyScopes.from(
                new ResolutionGraph(List.of(APP_NODE), List.of(), List.of()),
                new VersionSelectionResult(List.of(APP_NODE), List.of()),
                List.of(coverage, test));

        assertEquals(List.of(DependencyScope.TEST, DependencyScope.TOOL_COVERAGE), scopes.get(APP).stream()
                .map(SelectedDependencyScope::scope)
                .toList());
        assertEquals(Optional.of(descriptor), scopes.get(APP).get(1).artifactDescriptor());
    }
}
