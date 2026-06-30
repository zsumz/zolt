package com.zolt.resolve.framework;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.Coordinate;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.graph.PackageNode;
import com.zolt.resolve.graph.ResolutionEdge;
import com.zolt.resolve.graph.ResolutionGraph;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.request.RequestOrigin;
import com.zolt.resolve.traversal.DependencyTraversalDecision;
import com.zolt.resolve.version.VersionSelectionResult;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class FrameworkDependencyRequestPlanRequestAssemblerTest {
    private static final PackageId APP = new PackageId("com.example", "app");
    private static final PackageId LIB = new PackageId("com.example", "lib");
    private static final PackageNode APP_NODE = new PackageNode(APP, "1.0.0");
    private static final PackageNode LIB_NODE = new PackageNode(LIB, "2.0.0");

    private final FrameworkDependencyRequestPlanRequestAssembler assembler =
            new FrameworkDependencyRequestPlanRequestAssembler();

    @Test
    void assemblesSortedCandidatesAndVersionMapsForFrameworkPlanning() {
        DependencyRequest directCompile = new DependencyRequest(
                APP,
                "1.0.0",
                DependencyScope.COMPILE,
                RequestOrigin.DIRECT);
        DependencyRequest transitiveTest = new DependencyRequest(
                APP,
                "1.0.0",
                DependencyScope.TEST,
                RequestOrigin.TRANSITIVE);
        DependencyRequest platformRequest = new DependencyRequest(
                LIB,
                "2.0.0",
                DependencyScope.TOOL_SPRING_AOT,
                RequestOrigin.TRANSITIVE);

        FrameworkDependencyRequestPlanRequest request = assembler.assemble(
                config(),
                new ResolutionGraph(
                        List.of(LIB_NODE, APP_NODE),
                        List.of(new ResolutionEdge(
                                LIB_NODE,
                                APP_NODE,
                                transitiveTest,
                                DependencyTraversalDecision.include("test"))),
                        List.of()),
                new VersionSelectionResult(List.of(LIB_NODE, APP_NODE), List.of()),
                List.of(directCompile),
                Map.of(LIB, "2.0.0"),
                coordinate -> Path.of("cache").resolve(coordinate.artifactId() + ".jar"),
                () -> List.of(platformRequest));

        assertEquals(List.of(APP, LIB), request.candidates().stream()
                .map(FrameworkDependencyCandidate::packageId)
                .toList());
        assertEquals(List.of(DependencyScope.COMPILE, DependencyScope.TEST), request.candidates().getFirst()
                .selectedScopes());
        assertEquals("1.0.0", request.selectedVersions().get(APP));
        assertEquals("2.0.0", request.managedVersions().get(LIB));
        assertEquals(
                Path.of("cache", "app.jar"),
                request.artifactPathResolver().jarPath(new Coordinate(
                        APP.groupId(),
                        APP.artifactId(),
                        Optional.of("1.0.0"))));
        assertEquals(List.of(platformRequest), request.platformPropertiesRequests().get());
    }

    private static ProjectConfig config() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);
    }
}
