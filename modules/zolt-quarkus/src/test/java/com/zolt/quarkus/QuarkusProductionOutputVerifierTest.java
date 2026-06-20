package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusProductionOutputVerifierTest {
    @TempDir
    private Path projectDir;

    private final QuarkusProductionOutputVerifier verifier = new QuarkusProductionOutputVerifier();

    @Test
    void acceptsExistingFastJarPackageOutput() throws IOException {
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        Files.createDirectories(packageDirectory.resolve("lib"));
        Files.writeString(packageDirectory.resolve("quarkus-run.jar"), "runner", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> verifier.verify(
                descriptor(packageDirectory),
                summary(packageDirectory.resolve("quarkus-run.jar"), packageDirectory.resolve("lib"))));
    }

    @Test
    void acceptsExistingRunnerWithoutReportedLibraryDirectory() throws IOException {
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("quarkus-run.jar"), "runner", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> verifier.verify(
                descriptor(packageDirectory),
                summary(packageDirectory.resolve("quarkus-run.jar"), null)));
    }

    @Test
    void rejectsMissingPackageDirectory() {
        Path packageDirectory = projectDir.resolve("target/quarkus-app");

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> verifier.verify(
                        descriptor(packageDirectory),
                        summary(packageDirectory.resolve("quarkus-run.jar"), packageDirectory.resolve("lib"))));

        assertTrue(exception.getMessage().contains("did not create package directory"));
    }

    @Test
    void rejectsMissingRunnerJar() throws IOException {
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        Files.createDirectories(packageDirectory.resolve("lib"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> verifier.verify(
                        descriptor(packageDirectory),
                        summary(packageDirectory.resolve("quarkus-run.jar"), packageDirectory.resolve("lib"))));

        assertTrue(exception.getMessage().contains("did not create runner jar"));
    }

    @Test
    void rejectsMissingReportedLibraryDirectory() throws IOException {
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        Files.createDirectories(packageDirectory);
        Files.writeString(packageDirectory.resolve("quarkus-run.jar"), "runner", StandardCharsets.UTF_8);

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> verifier.verify(
                        descriptor(packageDirectory),
                        summary(packageDirectory.resolve("quarkus-run.jar"), packageDirectory.resolve("lib"))));

        assertTrue(exception.getMessage().contains("reported library directory"));
        assertTrue(exception.getMessage().contains("does not exist"));
    }

    private static QuarkusProductionApplicationSummary summary(Path jarPath, Path libraryDirectory) {
        return new QuarkusProductionApplicationSummary("result", 1, jarPath, libraryDirectory, false, null);
    }

    private static QuarkusBootstrapDescriptor descriptor(Path packageDirectory) {
        Path projectDirectory = packageDirectory.getParent().getParent();
        return new QuarkusBootstrapDescriptor(
                projectDirectory.resolve("target/quarkus/zolt-bootstrap.properties"),
                projectDirectory.resolve("target/quarkus/runtime-classpath.txt"),
                projectDirectory.resolve("target/quarkus/deployment-classpath.txt"),
                projectDirectory.resolve("target/quarkus/platform-properties.txt"),
                projectDirectory.resolve("target/quarkus/application-model.properties"),
                "io.quarkus.bootstrap.app.QuarkusBootstrap",
                "io.quarkus.bootstrap.app.AugmentAction",
                projectDirectory,
                projectDirectory.resolve("target/classes"),
                projectDirectory.resolve("target/quarkus"),
                packageDirectory,
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        projectDirectory.resolve("target/classes")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
