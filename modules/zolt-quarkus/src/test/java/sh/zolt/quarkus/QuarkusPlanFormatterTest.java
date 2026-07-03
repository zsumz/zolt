package sh.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import sh.zolt.quarkus.production.QuarkusAugmentationState;
import sh.zolt.quarkus.production.QuarkusOutputLayout;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class QuarkusPlanFormatterTest {
    private final QuarkusPlanFormatter formatter = new QuarkusPlanFormatter();

    @Test
    void formatsReadyPlanWithResolvedAndMissingExtensionDeploymentsInPlanOrder() {
        QuarkusPlan plan = plan(
                List.of(Path.of("/cache/app.jar"), Path.of("/cache/quarkus-rest.jar")),
                List.of(Path.of("/cache/quarkus-rest-deployment.jar")),
                new QuarkusAugmentationState(
                        Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                        QuarkusAugmentationState.Status.CURRENT,
                        Optional.of("sha256:" + "9".repeat(64))),
                List.of(
                        new QuarkusPlanExtension(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                Path.of("/cache/quarkus-rest.jar"),
                                QuarkusDeploymentArtifact.parse("io.quarkus:quarkus-rest-deployment:3.33.2"),
                                Optional.of(Path.of("/cache/quarkus-rest-deployment.jar"))),
                        new QuarkusPlanExtension(
                                new PackageId("io.quarkus", "quarkus-smallrye-openapi"),
                                Path.of("/cache/quarkus-smallrye-openapi.jar"),
                                QuarkusDeploymentArtifact.parse(
                                        "io.quarkus:quarkus-smallrye-openapi-deployment:3.33.2"),
                                Optional.empty())));

        String output = formatter.format(plan);

        assertTrue(output.contains("Status: inputs resolved; augmentation runner not implemented yet"));
        assertTrue(output.contains("Package target: fast-jar"));
        assertTrue(output.contains("Augmentation metadata: current "
                + "(/repo/target/quarkus/zolt-augmentation.properties; recorded sha256:"
                + "9".repeat(64)
                + ")"));
        assertTrue(output.contains("Runtime classpath entries: 2\n"
                + "  /cache/app.jar\n"
                + "  /cache/quarkus-rest.jar\n"));
        assertTrue(output.contains("Deployment classpath entries: 1\n"
                + "  /cache/quarkus-rest-deployment.jar\n"));
        assertTrue(output.contains("deployment jar: /cache/quarkus-rest-deployment.jar"));
        assertTrue(output.contains("deployment jar: missing from zolt.lock"));
        assertTrue(output.indexOf("io.quarkus:quarkus-rest ->")
                < output.indexOf("io.quarkus:quarkus-smallrye-openapi ->"));
        assertTrue(output.contains(
                "Next: implement the Zolt-owned Quarkus augmentation runner with these inputs."));
    }

    @Test
    void formatsNotReadyPlanWithActionableNextStep() {
        QuarkusPlan plan = plan(
                List.of(Path.of("/cache/app.jar")),
                List.of(),
                new QuarkusAugmentationState(
                        Path.of("/repo/target/quarkus/zolt-augmentation.properties"),
                        QuarkusAugmentationState.Status.MISSING,
                        Optional.empty()),
                List.of());

        String output = formatter.format(plan);

        assertTrue(output.contains("Status: not ready"));
        assertTrue(output.contains("Deployment classpath entries: 0\n"));
        assertTrue(output.contains("Quarkus extensions: 0\n"));
        assertTrue(output.contains(
                "Next: add a Quarkus extension dependency, run `zolt resolve`, "
                        + "then run `zolt quarkus plan` again."));
    }

    private static QuarkusPlan plan(
            List<Path> runtimeClasspath,
            List<Path> deploymentClasspath,
            QuarkusAugmentationState augmentationState,
            List<QuarkusPlanExtension> extensions) {
        return new QuarkusPlan(
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(
                        Path.of("/repo/target/quarkus"),
                        Path.of("/repo/target/quarkus-app")),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                "sha256:" + "1".repeat(64),
                augmentationState,
                runtimeClasspath,
                deploymentClasspath,
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        Path.of("/cache/quarkus-rest.jar"),
                        true)),
                extensions);
    }
}
