package sh.zolt.quarkus.testworker;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.PackageId;
import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.bootstrap.QuarkusWorkspaceModuleInputs;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusApplicationArtifact;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptor;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDescriptorWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestApplicationModelWorkerLauncherTest {
    @TempDir
    private Path tempDir;

    @Test
    void buildsIsolatedWorkerCommandWithDeploymentClasspathAndWorkspaceInputs() {
        QuarkusBootstrapDescriptor descriptor = descriptor();
        QuarkusWorkspaceModuleInputs workspaceModuleInputs = workspaceModuleInputs();
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(0, ""));

        assertEquals(List.of(
                "/jdk/bin/java",
                "-classpath",
                "/zolt/zolt.jar:/cache/quarkus-core-deployment.jar:/cache/quarkus-bootstrap-core.jar",
                QuarkusTestApplicationModelWorker.MAIN_CLASS,
                "/repo/target/quarkus/zolt-bootstrap.properties",
                "/repo/target/quarkus/test-application-model.dat",
                "/repo",
                "/repo/target",
                "/repo/src/main/java",
                "/repo/src/main/resources",
                "/repo/target/classes",
                "/repo/src/test/java",
                "/repo/src/test/resources",
                "/repo/target/test-classes"), launcher.command(
                        descriptor,
                        Path.of("/repo/target/quarkus/test-application-model.dat"),
                        workspaceModuleInputs));
    }

    @Test
    void returnsNormalizedOutputPathWhenWorkerWritesModel() {
        Path outputPath = tempDir.resolve("target/quarkus/test-application-model.dat");
        AtomicReference<List<String>> commandRef = new AtomicReference<>();
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> {
                    commandRef.set(command);
                    writeFile(outputPath, "serialized model");
                    return new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(
                            0,
                            "zolt.quarkus.test-app-model=" + outputPath + "\n");
                });

        Path result = launcher.write(descriptor(), outputPath, workspaceModuleInputs());

        assertEquals(outputPath.toAbsolutePath().normalize(), result);
        assertTrue(commandRef.get().contains(outputPath.toAbsolutePath().normalize().toString()));
    }

    @Test
    void failsWhenWorkerProcessFails() {
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(7, "bad inputs\n"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.write(descriptor(), tempDir.resolve("model.dat"), workspaceModuleInputs()));

        assertTrue(exception.getMessage().contains("failed with exit code 7"));
        assertTrue(exception.getMessage().contains("Fix the Quarkus test bootstrap inputs"));
        assertTrue(exception.getMessage().contains("bad inputs"));
    }

    @Test
    void failsWhenSuccessfulWorkerDoesNotWriteOutputFile() {
        Path outputPath = tempDir.resolve("target/quarkus/missing-model.dat");
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.write(descriptor(), outputPath, workspaceModuleInputs()));

        assertTrue(exception.getMessage().contains("completed without writing"));
        assertTrue(exception.getMessage().contains(outputPath.toAbsolutePath().normalize().toString()));
    }

    @Test
    void failsWhenOutputParentCannotBeCreated() throws IOException {
        Path blockedParent = tempDir.resolve("blocked");
        Files.writeString(blockedParent, "not a directory");
        Path outputPath = blockedParent.resolve("model.dat");
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.write(descriptor(), outputPath, workspaceModuleInputs()));

        assertTrue(exception.getMessage().contains("Could not create Quarkus test application model output directory"));
        assertTrue(exception.getMessage().contains("is writable and try again"));
    }

    @Test
    void requiresWorkspaceModuleInputs() {
        QuarkusTestApplicationModelWorkerLauncher launcher = new QuarkusTestApplicationModelWorkerLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(0, ""));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> launcher.write(descriptor(), tempDir.resolve("model.dat"), null));

        assertTrue(exception.getMessage().contains("Quarkus workspace module inputs are required"));
    }

    private static void writeFile(Path outputPath, String content) {
        try {
            Files.createDirectories(outputPath.getParent());
            Files.writeString(outputPath, content);
        } catch (IOException exception) {
            throw new AssertionError("Could not write fake worker output.", exception);
        }
    }

    private static QuarkusWorkspaceModuleInputs workspaceModuleInputs() {
        return new QuarkusWorkspaceModuleInputs(
                Path.of("/repo"),
                Path.of("/repo/target"),
                Path.of("/repo/src/main/java"),
                Path.of("/repo/src/main/resources"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/src/test/java"),
                Path.of("/repo/src/test/resources"),
                Path.of("/repo/target/test-classes"));
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
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
                List.of(),
                List.of());
    }
}
