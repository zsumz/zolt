package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusAnnotationClasspathSplitDiagnosticTest {
    @Test
    void reportsQuarkusBuilderSharedBetweenTestRuntimeAndDeploymentClasspaths() {
        Path quarkusBuilder = Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar");
        QuarkusAnnotationClasspathSplitDiagnostic diagnostic = new QuarkusAnnotationClasspathSplitDiagnostic(
                descriptorFile -> bootstrapDescriptor(List.of(
                        quarkusBuilder,
                        Path.of("/cache/io/quarkus/quarkus-deployment-3.33.2.jar"))));

        String output = diagnostic.describe(request(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusBuilder,
                Path.of("/cache/org/junit/platform/junit-platform-console.jar"))));

        assertTrue(output.contains("quarkus-builder-3.33.2.jar is present on both"));
        assertTrue(output.contains("JUnit test runtime classpath"));
        assertTrue(output.contains("Quarkus deployment classpath"));
        assertTrue(output.contains("Shared test/deployment classpath entries: 1"));
    }

    @Test
    void reportsWhenBootstrapDescriptorCannotBeInspected() {
        QuarkusAnnotationClasspathSplitDiagnostic diagnostic = new QuarkusAnnotationClasspathSplitDiagnostic(
                descriptorFile -> {
                    throw new QuarkusAugmentationException("missing descriptor");
                });

        String output = diagnostic.describe(request(List.of(Path.of("/repo/target/test-classes"))));

        assertTrue(output.contains("Could not inspect Quarkus deployment classpath ownership"));
        assertTrue(output.contains("/repo/target/quarkus/zolt-bootstrap.properties"));
        assertTrue(output.contains("Run `zolt build`, then run `zolt test` again"));
    }

    private static QuarkusAnnotationLaunchRequest request(List<Path> testRuntimeClasspath) {
        return new QuarkusAnnotationLaunchRequest(
                descriptor(testRuntimeClasspath),
                api(),
                List.of("com.example.HttpTest"),
                List.of("-Duser.dir=/repo"),
                testRuntimeClasspath,
                List.of("org.junit.platform.console.ConsoleLauncher"));
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

    private static QuarkusAnnotationApi api() {
        return new QuarkusAnnotationApi(
                "io.quarkus.test.junit.QuarkusTestExtension",
                "io.quarkus.test.junit.QuarkusTestProfile",
                "io.quarkus.test.junit.launcher.CustomLauncherInterceptor",
                List.of("io.quarkus.test.junit.launcher.JarLauncherProvider"));
    }
}
