package sh.zolt.quarkus.production;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
import sh.zolt.quarkus.QuarkusDeploymentArtifact;
import sh.zolt.quarkus.QuarkusPlan;
import sh.zolt.quarkus.QuarkusPlanExtension;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.QuarkusBootstrapWorkerResult;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusPlatformPropertiesArtifact;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class QuarkusBuildAugmentationServiceTestSupport {
    protected static ProjectConfig config(boolean quarkusEnabled) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        quarkusEnabled,
                        QuarkusPackageMode.FAST_JAR)));
    }

    protected static QuarkusPlan plan() {
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

    protected static QuarkusAugmentationRequest request() {
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

    protected static QuarkusAugmentationResult result(QuarkusAugmentationRequest request) {
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

    protected static QuarkusBootstrapDescriptor descriptor(QuarkusAugmentationRequest request) {
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

    protected static QuarkusOutputLayout outputLayout() {
        return new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app"));
    }

    protected static QuarkusApplicationArtifact applicationArtifact() {
        return new QuarkusApplicationArtifact(
                new PackageId("com.example", "demo"),
                "1.0.0",
                Path.of("/repo/target/classes"));
    }

    protected static QuarkusAugmentationState augmentationState() {
        return new QuarkusAugmentationState(
                Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                QuarkusAugmentationState.Status.MISSING,
                Optional.empty());
    }

    protected static List<Path> runtimeClasspath() {
        return List.of(Path.of("/cache/io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar"));
    }

    protected static List<Path> deploymentClasspath() {
        return List.of(Path.of("/cache/io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar"));
    }

    protected static List<QuarkusPlatformPropertiesArtifact> platformPropertiesArtifacts() {
        return List.of(new QuarkusPlatformPropertiesArtifact(
                new PackageId("io.quarkus.platform", "quarkus-bom-quarkus-platform-properties"),
                "3.33.0",
                Path.of("/cache/io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties")));
    }

    protected static List<QuarkusBootstrapDependency> bootstrapDependencies() {
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

    protected static QuarkusPlanExtension extension() {
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

    protected static String fingerprint() {
        return "sha256:" + "1".repeat(64);
    }
}
