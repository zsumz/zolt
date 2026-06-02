package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResolverModelTest {
    @Test
    void dependencyRequestMakesDirectAndRequestedVersionExplicit() {
        DependencyRequest request = new DependencyRequest(
                new PackageId("com.google.guava", "guava"),
                "33.4.0-jre",
                DependencyScope.COMPILE,
                RequestOrigin.DIRECT);

        assertTrue(request.direct());
        assertEquals("33.4.0-jre", request.requestedVersion());
    }

    @Test
    void packageNodeMakesSelectedVersionExplicit() {
        PackageNode node = new PackageNode(new PackageId("com.google.guava", "guava"), "33.4.0-jre");

        assertEquals("33.4.0-jre", node.selectedVersion());
    }

    @Test
    void versionConflictSeparatesRequestsFromSelectedVersion() {
        PackageId packageId = new PackageId("org.slf4j", "slf4j-api");
        VersionConflict conflict = new VersionConflict(
                packageId,
                List.of(
                        new DependencyRequest(packageId, "2.0.15", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE),
                        new DependencyRequest(packageId, "2.0.16", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE)),
                "2.0.16",
                ConflictSelectionReason.NEWEST_VERSION);

        assertEquals(List.of("2.0.15", "2.0.16"), conflict.requests().stream().map(DependencyRequest::requestedVersion).toList());
        assertEquals("2.0.16", conflict.selectedVersion());
        assertEquals(ConflictSelectionReason.NEWEST_VERSION, conflict.selectionReason());
    }

    @Test
    void graphCollectionsAreImmutable() {
        List<PackageNode> nodes = new ArrayList<>();
        nodes.add(new PackageNode(new PackageId("com.example", "demo"), "1.0.0"));
        ResolutionGraph graph = new ResolutionGraph(nodes, List.of(), List.of());
        nodes.clear();

        assertEquals(1, graph.nodes().size());
        assertThrows(UnsupportedOperationException.class, () -> graph.nodes().add(
                new PackageNode(new PackageId("com.example", "other"), "1.0.0")));
    }

    @Test
    void resolvedPackageCarriesClasspathInputsWithoutHttpOrCliConcerns() {
        ResolvedPackage resolvedPackage = new ResolvedPackage(
                new PackageId("com.google.guava", "guava"),
                "33.4.0-jre",
                true,
                Path.of("cache/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"),
                Path.of("cache/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar"));

        assertTrue(resolvedPackage.direct());
        assertEquals("33.4.0-jre", resolvedPackage.selectedVersion());
        assertEquals("guava-33.4.0-jre.jar", resolvedPackage.jarPath().getFileName().toString());
    }
}
