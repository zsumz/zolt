package com.zolt.plan;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.PublicationMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildPlanServiceTest {
    private final BuildPlanService service = new BuildPlanService();

    @TempDir
    private Path projectDir;

    @Test
    void plansCoveragePackageAndPublishOutputsUnderConfiguredOutputRoot() {
        ProjectConfig config = ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                ".zolt/build",
                                ".zolt/build/classes",
                                ".zolt/build/test-classes"))
                .withPackageSettings(new PackageSettings(
                        PackageMode.THIN,
                        true,
                        true,
                        true,
                        PublicationMetadata.empty()));

        BuildPlan plan = service.plan(projectDir, config, PlanTarget.CI, Optional.empty());

        PlanNode coverage = node(plan, "coverage");
        assertEquals(List.of(".zolt/build/test-classes", "zolt.lock"), coverage.inputs());
        assertEquals(List.of(".zolt/build/coverage"), coverage.outputs());

        PlanNode packageNode = node(plan, "assemble-package");
        assertEquals(List.of(
                ".zolt/build/demo-1.0.0.jar",
                ".zolt/build/demo-1.0.0-sources.jar",
                ".zolt/build/demo-1.0.0-javadoc.jar",
                ".zolt/build/demo-1.0.0-tests.jar"), packageNode.outputs());

        PlanNode publish = node(plan, "publish-dry-run");
        assertTrue(publish.inputs().contains(".zolt/build/demo-1.0.0.jar"));
        assertTrue(publish.inputs().contains("zolt.lock"));
        assertEquals(List.of(".zolt/build/publish"), publish.outputs());
    }

    private static PlanNode node(BuildPlan plan, String id) {
        return plan.nodes().stream()
                .filter(node -> node.id().equals(id))
                .findFirst()
                .orElseThrow();
    }
}
