package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyScope;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusBuildAugmentationServiceTest {
    @Test
    void skipsAugmentationWhenQuarkusIsDisabled() {
        boolean[] planned = new boolean[] {false};
        boolean[] ran = new boolean[] {false};
        QuarkusBuildAugmentationService service = new QuarkusBuildAugmentationService(
                (projectDirectory, config, cacheRoot) -> {
                    planned[0] = true;
                    return plan();
                },
                plan -> request(),
                (config, request) -> {
                    ran[0] = true;
                    return result(request);
                });

        Optional<QuarkusAugmentationResult> result = service.augmentIfEnabled(
                Path.of("/repo"),
                config(false),
                Path.of("/cache"));

        assertTrue(result.isEmpty());
        assertFalse(planned[0]);
        assertFalse(ran[0]);
    }

    @Test
    void runsAugmentationForQuarkusEnabledProject() {
        Path projectDirectory = Path.of("/repo");
        Path cacheRoot = Path.of("/cache");
        ProjectConfig config = config(true);
        QuarkusPlan plan = plan();
        QuarkusAugmentationRequest request = request();
        QuarkusAugmentationResult expected = result(request);
        QuarkusBuildAugmentationService service = new QuarkusBuildAugmentationService(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> {
                    assertEquals(projectDirectory, actualProjectDirectory);
                    assertSame(config, actualConfig);
                    assertEquals(cacheRoot, actualCacheRoot);
                    return plan;
                },
                actualPlan -> {
                    assertSame(plan, actualPlan);
                    return request;
                },
                (actualConfig, actualRequest) -> {
                    assertSame(config, actualConfig);
                    assertSame(request, actualRequest);
                    return expected;
                });

        Optional<QuarkusAugmentationResult> result = service.augmentIfEnabled(projectDirectory, config, cacheRoot);

        assertTrue(result.isPresent());
        assertSame(expected, result.orElseThrow());
    }

    @Test
    void requiresBuildInputs() {
        QuarkusBuildAugmentationService service = new QuarkusBuildAugmentationService(
                (projectDirectory, config, cacheRoot) -> plan(),
                plan -> request(),
                (config, request) -> result(request));

        assertThrows(
                QuarkusAugmentationException.class,
                () -> service.augmentIfEnabled(null, config(true), Path.of("/cache")));
        assertThrows(
                QuarkusAugmentationException.class,
                () -> service.augmentIfEnabled(Path.of("/repo"), null, Path.of("/cache")));
        assertThrows(
                QuarkusAugmentationException.class,
                () -> service.augmentIfEnabled(Path.of("/repo"), config(true), null));
    }

    private static ProjectConfig config(boolean quarkusEnabled) {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        quarkusEnabled,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private static QuarkusPlan plan() {
        return new QuarkusPlan(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                outputLayout(),
                applicationArtifact(),
                fingerprint(),
                augmentationState(),
                runtimeClasspath(),
                deploymentClasspath(),
                platformPropertiesArtifacts(),
                bootstrapDependencies(),
                List.of(extension()));
    }

    private static QuarkusAugmentationRequest request() {
        return new QuarkusAugmentationRequest(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                outputLayout(),
                applicationArtifact(),
                fingerprint(),
                Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                runtimeClasspath(),
                deploymentClasspath(),
                platformPropertiesArtifacts(),
                bootstrapDependencies(),
                List.of(extension()));
    }

    private static QuarkusAugmentationResult result(QuarkusAugmentationRequest request) {
        QuarkusBootstrapWorkerResult workerResult = new QuarkusBootstrapWorkerResult(
                request.inputFingerprint(),
                request.outputLayout().packageDirectory(),
                request.outputLayout().packageDirectory().resolve("quarkus-run.jar"),
                request.outputLayout().packageDirectory().resolve("lib"),
                1);
        return new QuarkusAugmentationResult(
                request.outputLayout().augmentationDirectory(),
                request.metadataPath(),
                descriptor(request),
                request.inputFingerprint(),
                workerResult);
    }

    private static QuarkusBootstrapDescriptor descriptor(QuarkusAugmentationRequest request) {
        return new QuarkusBootstrapDescriptor(
                request.outputLayout().augmentationDirectory().resolve("bootstrap.properties"),
                request.outputLayout().augmentationDirectory().resolve("runtime-classpath.txt"),
                request.outputLayout().augmentationDirectory().resolve("deployment-classpath.txt"),
                request.outputLayout().augmentationDirectory().resolve("platform-properties.txt"),
                request.outputLayout().augmentationDirectory().resolve("application-model.properties"),
                "io.quarkus.bootstrap.app.QuarkusBootstrap",
                "io.quarkus.bootstrap.app.AugmentAction",
                request.projectDirectory(),
                request.applicationClasses(),
                request.outputLayout().augmentationDirectory(),
                request.outputLayout().packageDirectory(),
                "fast-jar",
                request.inputFingerprint(),
                request.applicationArtifact(),
                request.runtimeClasspath(),
                request.deploymentClasspath(),
                request.platformPropertiesArtifacts().stream()
                        .map(QuarkusPlatformPropertiesArtifact::path)
                        .toList(),
                request.bootstrapDependencies());
    }

    private static QuarkusOutputLayout outputLayout() {
        return new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app"));
    }

    private static QuarkusApplicationArtifact applicationArtifact() {
        return new QuarkusApplicationArtifact(
                new PackageId("com.example", "demo"),
                "1.0.0",
                Path.of("/repo/target/classes"));
    }

    private static QuarkusAugmentationState augmentationState() {
        return new QuarkusAugmentationState(
                Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                QuarkusAugmentationState.Status.MISSING,
                Optional.empty());
    }

    private static List<Path> runtimeClasspath() {
        return List.of(Path.of("/cache/io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"));
    }

    private static List<Path> deploymentClasspath() {
        return List.of(Path.of(
                "/cache/io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"));
    }

    private static List<QuarkusPlatformPropertiesArtifact> platformPropertiesArtifacts() {
        return List.of(new QuarkusPlatformPropertiesArtifact(
                new PackageId("io.quarkus.platform", "quarkus-bom-quarkus-platform-properties"),
                "3.33.0",
                Path.of("/cache/io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties")));
    }

    private static List<QuarkusBootstrapDependency> bootstrapDependencies() {
        return List.of(
                new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        "3.33.0",
                        DependencyScope.COMPILE,
                        runtimeClasspath().getFirst(),
                        true),
                new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest-deployment"),
                        "3.33.0",
                        DependencyScope.QUARKUS_DEPLOYMENT,
                        deploymentClasspath().getFirst(),
                        false));
    }

    private static QuarkusPlanExtension extension() {
        return new QuarkusPlanExtension(
                new PackageId("io.quarkus", "quarkus-rest"),
                runtimeClasspath().getFirst(),
                new QuarkusDeploymentArtifact(
                        "io.quarkus",
                        "quarkus-rest-deployment",
                        Optional.empty(),
                        "jar",
                        "3.33.0"),
                Optional.of(deploymentClasspath().getFirst()));
    }

    private static String fingerprint() {
        return "sha256:" + "1".repeat(64);
    }
}
