package com.zolt.quarkus.annotation.diagnostic;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.annotation.QuarkusAnnotationApi;
import com.zolt.quarkus.annotation.launcher.QuarkusAnnotationLaunchRequest;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.testworker.descriptor.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.testworker.QuarkusTestRunnerRequest;
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

    @Test
    void reportsWhenQuarkusBuilderIsMissingFromLauncherClasspath() {
        QuarkusAnnotationClasspathSplitDiagnostic diagnostic = new QuarkusAnnotationClasspathSplitDiagnostic(
                descriptorFile -> bootstrapDescriptor(List.of()));

        String output = diagnostic.describeMissingBuilderApi(request(List.of(
                Path.of("/repo/target/test-classes"),
                Path.of("/cache/org/junit/platform/junit-platform-console.jar"))));

        assertTrue(output.contains("quarkus-builder is absent from the annotation JVM launcher classpath"));
        assertTrue(output.contains("io.quarkus.builder.item.MultiBuildItem"));
    }

    @Test
    void reportsWhenQuarkusBuilderIsPresentOnLauncherClasspath() {
        QuarkusAnnotationClasspathSplitDiagnostic diagnostic = new QuarkusAnnotationClasspathSplitDiagnostic(
                descriptorFile -> bootstrapDescriptor(List.of()));

        String output = diagnostic.describeMissingBuilderApi(request(List.of(
                Path.of("/repo/target/test-classes"),
                Path.of("/cache/io/quarkus/quarkus-builder-3.33.2.jar"))));

        assertTrue(output.contains("quarkus-builder-3.33.2.jar is present on the annotation JVM launcher classpath"));
        assertTrue(output.contains("io.quarkus.builder.item.MultiBuildItem"));
    }

    @Test
    void reportsRuntimeServiceProviderSplitOwnership() {
        Path quarkusCore = Path.of("/cache/io/quarkus/quarkus-core-3.33.2.jar");
        Path quarkusRest = Path.of("/cache/io/quarkus/quarkus-rest-3.33.2.jar");
        Path quarkusRestCommon = Path.of("/cache/io/quarkus/quarkus-rest-common-3.33.2.jar");
        QuarkusAnnotationClasspathSplitDiagnostic diagnostic = new QuarkusAnnotationClasspathSplitDiagnostic(
                descriptorFile -> bootstrapDescriptor(List.of(quarkusCore, quarkusRest)));

        String output = diagnostic.describeRuntimeServiceProviderSplit(request(List.of(
                Path.of("/repo/target/test-classes"),
                quarkusRestCommon,
                quarkusCore,
                quarkusRest)));

        assertTrue(output.contains("quarkus-core-3.33.2.jar provides io.quarkus.runtime.test.TestHttpEndpointProvider"));
        assertTrue(output.contains("annotation JVM launcher classpath and is also present on the Quarkus deployment classpath"));
        assertTrue(output.contains("quarkus-rest-3.33.2.jar provides io.quarkus.resteasy.reactive.server.runtime.ResteasyReactiveTestHttpProvider"));
        assertTrue(output.contains("through META-INF/services and is also present on the Quarkus deployment classpath"));
        assertTrue(output.contains("one classloader identity for both the service type and provider"));
        assertTrue(output.contains("condition/config evaluation on the system classloader"));
        assertTrue(output.contains("defers the runtime context-classloader handoff"));
        assertTrue(output.contains("current blocking failure is runtime service loading"));
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
