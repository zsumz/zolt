package sh.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import sh.zolt.quarkus.production.QuarkusAugmentationState;
import sh.zolt.quarkus.production.QuarkusOutputLayout;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusPlanValueTest {
    @Test
    void normalizesOptionalCollectionsAndCopiesMutableInputs() {
        List<Path> runtimeClasspath = new ArrayList<>(List.of(Path.of("/cache/app.jar")));
        List<Path> deploymentClasspath = new ArrayList<>(List.of(Path.of("/cache/quarkus-rest-deployment.jar")));
        QuarkusPlan plan = new QuarkusPlan(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app")),
                applicationArtifact(),
                "sha256:" + "1".repeat(64),
                state(),
                runtimeClasspath,
                deploymentClasspath,
                null,
                null,
                null);

        runtimeClasspath.add(Path.of("/cache/late-runtime.jar"));
        deploymentClasspath.clear();

        assertEquals(List.of(Path.of("/cache/app.jar")), plan.runtimeClasspath());
        assertEquals(List.of(Path.of("/cache/quarkus-rest-deployment.jar")), plan.deploymentClasspath());
        assertEquals(List.of(), plan.platformPropertiesArtifacts());
        assertEquals(List.of(), plan.bootstrapDependencies());
        assertEquals(List.of(), plan.extensions());
        assertTrue(plan.hasDeploymentInputs());
        assertTrue(plan.allExtensionDeploymentsResolved());
        assertThrows(UnsupportedOperationException.class, () -> plan.runtimeClasspath().add(Path.of("/cache/other.jar")));
    }

    @Test
    void detectsMissingExtensionDeploymentArtifacts() {
        QuarkusPlan plan = new QuarkusPlan(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app")),
                applicationArtifact(),
                "sha256:" + "1".repeat(64),
                state(),
                List.of(Path.of("/cache/app.jar")),
                List.of(Path.of("/cache/quarkus-rest-deployment.jar")),
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        Path.of("/cache/quarkus-rest.jar"),
                        true)),
                List.of(new QuarkusPlanExtension(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        Path.of("/cache/quarkus-rest.jar"),
                        QuarkusDeploymentArtifact.parse("io.quarkus:quarkus-rest-deployment:3.33.2"),
                        Optional.empty())));

        assertFalse(plan.allExtensionDeploymentsResolved());
    }

    @Test
    void rejectsMissingRequiredFieldsWithActionableMessages() {
        QuarkusPlanException packageMode = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlan(
                        Path.of("/repo"),
                        Path.of("/repo/target/classes"),
                        null,
                        new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app")),
                        applicationArtifact(),
                        "sha256:" + "1".repeat(64),
                        state(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        assertTrue(packageMode.getMessage().contains("requires a package mode"));

        QuarkusPlanException fingerprint = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlan(
                        Path.of("/repo"),
                        Path.of("/repo/target/classes"),
                        QuarkusPackageMode.FAST_JAR,
                        new QuarkusOutputLayout(Path.of("/repo/target/quarkus"), Path.of("/repo/target/quarkus-app")),
                        applicationArtifact(),
                        " ",
                        state(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()));
        assertTrue(fingerprint.getMessage().contains("requires an input fingerprint"));
    }

    private static QuarkusApplicationArtifact applicationArtifact() {
        return new QuarkusApplicationArtifact(
                new PackageId("com.example", "demo"),
                "1.0.0",
                Path.of("/repo/target/classes"));
    }

    private static QuarkusAugmentationState state() {
        return new QuarkusAugmentationState(
                Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                QuarkusAugmentationState.Status.MISSING,
                Optional.empty());
    }
}
