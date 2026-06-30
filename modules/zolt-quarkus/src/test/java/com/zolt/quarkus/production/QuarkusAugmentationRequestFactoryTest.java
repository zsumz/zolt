package com.zolt.quarkus.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.quarkus.QuarkusDeploymentArtifact;
import com.zolt.quarkus.QuarkusPlan;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.QuarkusPlanExtension;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import com.zolt.quarkus.bootstrap.descriptor.QuarkusPlatformPropertiesArtifact;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusAugmentationRequestFactoryTest {
    private final QuarkusAugmentationRequestFactory factory = new QuarkusAugmentationRequestFactory();

    @Test
    void createsRequestFromReadyPlan() {
        QuarkusPlan plan = readyPlan();

        QuarkusAugmentationRequest request = factory.create(plan);

        assertEquals(plan.projectDirectory(), request.projectDirectory());
        assertEquals(plan.applicationClasses(), request.applicationClasses());
        assertEquals(QuarkusPackageMode.FAST_JAR, request.packageMode());
        assertEquals(plan.outputLayout(), request.outputLayout());
        assertEquals(plan.applicationArtifact(), request.applicationArtifact());
        assertEquals(plan.inputFingerprint(), request.inputFingerprint());
        assertEquals(plan.augmentationState().metadataPath(), request.metadataPath());
        assertEquals(plan.runtimeClasspath(), request.runtimeClasspath());
        assertEquals(plan.deploymentClasspath(), request.deploymentClasspath());
        assertEquals(plan.platformPropertiesArtifacts(), request.platformPropertiesArtifacts());
        assertEquals(plan.bootstrapDependencies(), request.bootstrapDependencies());
        assertEquals(plan.extensions(), request.extensions());
    }

    @Test
    void rejectsPlanWithoutDeploymentInputs() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> factory.create(plan(List.of(), List.of(extensionWithDeployment()))));

        assertTrue(exception.getMessage().contains("No Quarkus deployment artifacts were found in zolt.lock"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
    }

    @Test
    void rejectsPlanWithMissingExtensionDeploymentMapping() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> factory.create(plan(
                        List.of(Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                        List.of(extensionWithoutDeployment()))));

        assertTrue(exception.getMessage().contains("runtime extensions do not have matching deployment artifacts"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve`"));
    }

    @Test
    void requestListsAreImmutable() {
        QuarkusAugmentationRequest request = factory.create(readyPlan());

        assertThrows(UnsupportedOperationException.class, () -> request.runtimeClasspath().add(Path.of("other")));
        assertThrows(UnsupportedOperationException.class, () -> request.deploymentClasspath().add(Path.of("other")));
        assertThrows(UnsupportedOperationException.class, () -> request.platformPropertiesArtifacts().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.bootstrapDependencies().clear());
        assertThrows(UnsupportedOperationException.class, () -> request.extensions().clear());
    }

    private static QuarkusPlan readyPlan() {
        return plan(
                List.of(Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of(extensionWithDeployment()));
    }

    private static QuarkusPlan plan(
            List<Path> deploymentClasspath,
            List<QuarkusPlanExtension> extensions) {
        return new QuarkusPlan(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app")),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                "sha256:" + "1".repeat(64),
                new QuarkusAugmentationState(
                        Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                        QuarkusAugmentationState.Status.MISSING,
                Optional.empty()),
                List.of(Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest.jar")),
                deploymentClasspath,
                platformPropertiesArtifacts(),
                bootstrapDependencies(deploymentClasspath),
                extensions);
    }

    private static List<QuarkusPlatformPropertiesArtifact> platformPropertiesArtifacts() {
        return List.of(new QuarkusPlatformPropertiesArtifact(
                new PackageId("io.quarkus.platform", "quarkus-bom-quarkus-platform-properties"),
                "3.33.0",
                Path.of("/repo/.zolt/cache/io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.0/quarkus-bom-quarkus-platform-properties-3.33.0.properties")));
    }

    private static List<QuarkusBootstrapDependency> bootstrapDependencies(List<Path> deploymentClasspath) {
        List<QuarkusBootstrapDependency> dependencies = new java.util.ArrayList<>();
        dependencies.add(new QuarkusBootstrapDependency(
                new PackageId("io.quarkus", "quarkus-rest"),
                "3.33.0",
                DependencyScope.COMPILE,
                Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest.jar"),
                true));
        for (Path path : deploymentClasspath) {
            dependencies.add(new QuarkusBootstrapDependency(
                    new PackageId("io.quarkus", "quarkus-rest-deployment"),
                    "3.33.0",
                    DependencyScope.QUARKUS_DEPLOYMENT,
                    path,
                    false));
        }
        return List.copyOf(dependencies);
    }

    private static QuarkusPlanExtension extensionWithDeployment() {
        return new QuarkusPlanExtension(
                new PackageId("io.quarkus", "quarkus-rest"),
                Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest.jar"),
                deploymentArtifact(),
                Optional.of(Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest-deployment.jar")));
    }

    private static QuarkusPlanExtension extensionWithoutDeployment() {
        return new QuarkusPlanExtension(
                new PackageId("io.quarkus", "quarkus-rest"),
                Path.of("/repo/.zolt/cache/io/quarkus/quarkus-rest.jar"),
                deploymentArtifact(),
                Optional.empty());
    }

    private static QuarkusDeploymentArtifact deploymentArtifact() {
        return new QuarkusDeploymentArtifact(
                "io.quarkus",
                "quarkus-rest-deployment",
                Optional.empty(),
                "jar",
                "3.33.0");
    }
}
