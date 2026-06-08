package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusProductionOutputValidatorTest {
    private final QuarkusProductionOutputValidator validator = new QuarkusProductionOutputValidator();

    @Test
    void acceptsFastJarOutputInPlannedPackageDirectory() {
        QuarkusBootstrapDescriptor descriptor = descriptor("fast-jar", Path.of("/repo/target/quarkus-app"));
        QuarkusProductionApplicationSummary summary = summary(
                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                Path.of("/repo/target/quarkus-app/lib"),
                false);

        assertDoesNotThrow(() -> validator.validate(descriptor, summary));
    }

    @Test
    void acceptsFastJarWithoutLibraryDirectory() {
        QuarkusBootstrapDescriptor descriptor = descriptor("fast-jar", Path.of("/repo/target/quarkus-app"));
        QuarkusProductionApplicationSummary summary = summary(
                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                null,
                false);

        assertDoesNotThrow(() -> validator.validate(descriptor, summary));
    }

    @Test
    void rejectsUnsupportedPackageMode() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> validator.validate(
                        descriptor("legacy-jar", Path.of("/repo/target/quarkus-app")),
                        summary(
                                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                                Path.of("/repo/target/quarkus-app/lib"),
                                false)));

        assertTrue(exception.getMessage().contains("fast-jar only"));
    }

    @Test
    void rejectsMissingJarResult() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> validator.validate(
                        descriptor("fast-jar", Path.of("/repo/target/quarkus-app")),
                        new QuarkusProductionApplicationSummary("result", 1, null, null, false, null)));

        assertTrue(exception.getMessage().contains("did not report a jar result"));
    }

    @Test
    void rejectsUberJarResult() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> validator.validate(
                        descriptor("fast-jar", Path.of("/repo/target/quarkus-app")),
                        summary(Path.of("/repo/target/quarkus-app/quarkus-run.jar"), null, true)));

        assertTrue(exception.getMessage().contains("reported an uber jar"));
    }

    @Test
    void rejectsRunnerJarOutsidePlannedPackageDirectory() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> validator.validate(
                        descriptor("fast-jar", Path.of("/repo/target/quarkus-app")),
                        summary(
                                Path.of("/repo/target/other/quarkus-run.jar"),
                                Path.of("/repo/target/quarkus-app/lib"),
                                false)));

        assertTrue(exception.getMessage().contains("but Zolt expected"));
        assertTrue(exception.getMessage().contains("quarkus-run.jar"));
    }

    @Test
    void rejectsLibraryDirectoryOutsidePlannedPackageDirectory() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> validator.validate(
                        descriptor("fast-jar", Path.of("/repo/target/quarkus-app")),
                        summary(
                                Path.of("/repo/target/quarkus-app/quarkus-run.jar"),
                                Path.of("/repo/target/other-lib"),
                                false)));

        assertTrue(exception.getMessage().contains("outside planned package directory"));
    }

    private static QuarkusProductionApplicationSummary summary(Path jarPath, Path libraryDirectory, boolean uberJar) {
        return new QuarkusProductionApplicationSummary("result", 1, jarPath, libraryDirectory, uberJar, null);
    }

    private static QuarkusBootstrapDescriptor descriptor(String packageMode, Path packageDirectory) {
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
                packageDirectory,
                packageMode,
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }
}
