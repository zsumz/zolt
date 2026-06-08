package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.PackageId;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class QuarkusBootstrapWorkerLauncherTest {
    @Test
    void buildsIsolatedWorkerCommandWithDeploymentClasspath() {
        QuarkusBootstrapDescriptor descriptor = descriptor();
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(0, ""));

        assertEquals(List.of(
                "/jdk/bin/java",
                "-classpath",
                "/zolt/zolt.jar:/cache/quarkus-core-deployment.jar:/cache/quarkus-bootstrap-core.jar",
                QuarkusBootstrapWorker.MAIN_CLASS,
                "/repo/target/quarkus/zolt-bootstrap.properties"), launcher.command(descriptor));
    }

    @Test
    void succeedsWhenWorkerEmitsMatchingResultMarker() {
        QuarkusBootstrapDescriptor descriptor = descriptor();
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(0, workerOutput(descriptor)));

        QuarkusBootstrapWorkerResult result = launcher.augment(null, descriptor);

        assertEquals(descriptor.inputFingerprint(), result.inputFingerprint());
        assertEquals(descriptor.packageDirectory(), result.packageDirectory());
    }

    @Test
    void failsWhenWorkerProcessFails() {
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(3, "not implemented\n"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.augment(null, descriptor()));

        assertTrue(exception.getMessage().contains("Quarkus bootstrap worker failed with exit code 3"));
        assertTrue(exception.getMessage().contains("not implemented"));
    }

    @Test
    void rejectsSuccessfulWorkerWithoutResultMarker() {
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(0, "plain output\n"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.augment(null, descriptor()));

        assertTrue(exception.getMessage().contains("without a Zolt success marker"));
    }

    @Test
    void rejectsWorkerResultWithUnexpectedFingerprint() {
        QuarkusBootstrapDescriptor descriptor = descriptor();
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(
                        0,
                        workerOutput(new QuarkusBootstrapWorkerResult(
                                "sha256:" + "2".repeat(64),
                                descriptor.packageDirectory(),
                                descriptor.packageDirectory().resolve("quarkus-run.jar"),
                                descriptor.packageDirectory().resolve("lib"),
                                1))));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.augment(null, descriptor));

        assertTrue(exception.getMessage().contains("did not match expected fingerprint"));
    }

    @Test
    void rejectsWorkerResultWithUnexpectedPackageDirectory() {
        QuarkusBootstrapDescriptor descriptor = descriptor();
        QuarkusBootstrapWorkerLauncher launcher = new QuarkusBootstrapWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(
                        0,
                        workerOutput(new QuarkusBootstrapWorkerResult(
                                descriptor.inputFingerprint(),
                                Path.of("/repo/target/other-quarkus-app"),
                                Path.of("/repo/target/other-quarkus-app/quarkus-run.jar"),
                                Path.of("/repo/target/other-quarkus-app/lib"),
                                1))));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.augment(null, descriptor));

        assertTrue(exception.getMessage().contains("did not match expected package directory"));
    }

    @Test
    void requiresWorkerClasspath() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> new QuarkusBootstrapWorkerLauncher(
                        ":",
                        Path.of("/jdk/bin/java"),
                        List.of(),
                        command -> new QuarkusBootstrapWorkerLauncher.ProcessResult(0, "")));

        assertTrue(exception.getMessage().contains("worker classpath is required"));
    }

    private static String workerOutput(QuarkusBootstrapDescriptor descriptor) {
        return workerOutput(new QuarkusBootstrapWorkerResult(
                descriptor.inputFingerprint(),
                descriptor.packageDirectory(),
                descriptor.packageDirectory().resolve("quarkus-run.jar"),
                descriptor.packageDirectory().resolve("lib"),
                1));
    }

    private static String workerOutput(QuarkusBootstrapWorkerResult result) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new QuarkusBootstrapWorkerResultCodec().write(
                new PrintStream(output, true, StandardCharsets.UTF_8),
                result);
        return output.toString(StandardCharsets.UTF_8);
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                QuarkusBootstrapDescriptorWriter.BOOTSTRAP_CLASS,
                QuarkusBootstrapDescriptorWriter.AUGMENT_ACTION_CLASS,
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/quarkus"),
                Path.of("/repo/target/quarkus-app"),
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                List.of(Path.of("/cache/quarkus-rest.jar")),
                List.of(
                        Path.of("/cache/quarkus-core-deployment.jar"),
                        Path.of("/cache/quarkus-bootstrap-core.jar")),
                List.of());
    }
}
