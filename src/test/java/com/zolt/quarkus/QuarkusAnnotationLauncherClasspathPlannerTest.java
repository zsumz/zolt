package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationLauncherClasspathPlannerTest {
    @Test
    void preservesFullLauncherClasspathAndClassifiesSharedBuilderArtifact() {
        Path quarkusBuilder = Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar");
        Path junitConsole = Path.of("/cache/org/junit/platform/junit-platform-console.jar");
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of(
                        quarkusBuilder,
                        Path.of("/cache/io/quarkus/quarkus-deployment-3.33.2.jar"))));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusBuilder,
                junitConsole)));

        assertEquals(List.of(Path.of("/repo/target/test-classes"), quarkusBuilder, junitConsole), plan.launcherClasspath());
        assertEquals(List.of(quarkusBuilder.toAbsolutePath().normalize()), plan.splitSensitiveArtifacts());
        assertTrue(plan.builderApiVisible());
        assertEquals(1, plan.sharedDeploymentEntries());
    }

    @Test
    void reportsBuilderApiVisibilityWithoutDeploymentOverlap() {
        Path quarkusBuilder = Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar");
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> bootstrapDescriptor(List.of(Path.of("/cache/io/quarkus/quarkus-deployment-3.33.2.jar"))));

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusBuilder)));

        assertTrue(plan.builderApiVisible());
        assertEquals(List.of(), plan.splitSensitiveArtifacts());
        assertEquals(0, plan.sharedDeploymentEntries());
    }

    @Test
    void keepsLauncherClasspathWhenBootstrapDescriptorCannotBeInspected() {
        QuarkusAnnotationLauncherClasspathPlanner planner = new QuarkusAnnotationLauncherClasspathPlanner(
                descriptorFile -> {
                    throw new QuarkusAugmentationException("missing descriptor");
                });

        QuarkusAnnotationLauncherClasspathPlan plan = planner.plan(descriptor(List.of(
                Path.of("/repo/target/test-classes"),
                Path.of("/cache/org/junit/platform/junit-platform-console.jar"))));

        assertEquals(
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/cache/org/junit/platform/junit-platform-console.jar")),
                plan.launcherClasspath());
        assertFalse(plan.builderApiVisible());
        assertEquals(List.of(), plan.splitSensitiveArtifacts());
        assertEquals(0, plan.sharedDeploymentEntries());
    }

    private static QuarkusTestRunnerDescriptor descriptor(List<Path> testRuntimeClasspath) {
        return new QuarkusTestRunnerDescriptor(
                Path.of("/repo/target/quarkus/zolt-test-bootstrap.properties"),
                Path.of("/repo/target/quarkus/test-runtime-classpath.txt"),
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/test-classes"),
                Path.of("/repo/target/quarkus/test-application-model.dat"),
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                QuarkusTestRunnerRequest.RUNNER_MODE,
                true,
                true,
                testRuntimeClasspath);
    }

    private static QuarkusBootstrapDescriptor bootstrapDescriptor(List<Path> deploymentClasspath) {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                "io.quarkus.bootstrap.app.QuarkusBootstrap",
                "io.quarkus.bootstrap.app.AugmentAction",
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/quarkus"),
                Path.of("/repo/target/quarkus-app"),
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(new PackageId("com.example", "demo"), "1.0.0", Path.of("/repo/target/classes")),
                List.of(Path.of("/repo/target/classes")),
                deploymentClasspath,
                List.of(),
                List.of());
    }
}
