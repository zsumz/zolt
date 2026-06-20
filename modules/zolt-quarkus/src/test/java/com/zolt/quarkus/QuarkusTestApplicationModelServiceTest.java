package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.doctor.JdkDetector;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusTestApplicationModelServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void rejectsQuarkusModelOutputSymlinkThatEscapesProject() throws IOException {
        createSymlink(projectDir.resolve("target"), Files.createTempDirectory("zolt-quarkus-model-target-"));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> service().writeIfEnabled(projectDir, config(BuildSettings.defaults())));

        assertTrue(exception.getMessage().contains("Quarkus test application model output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void rejectsUnsafeWorkspaceModuleSourcePath() throws IOException {
        writeBootstrapDescriptor();

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> service().writeIfEnabled(
                        projectDir,
                        config(new BuildSettings(
                                "../outside-source",
                                "src/test/java",
                                "target/classes",
                                "target/test-classes"))));

        assertTrue(exception.getMessage().contains("[build].source"));
        assertTrue(exception.getMessage().contains("../outside-source"));
    }

    private QuarkusTestApplicationModelService service() {
        return new QuarkusTestApplicationModelService(
                new QuarkusBootstrapDescriptorReader(),
                new JdkDetector(),
                () -> List.of(projectDir.resolve("zolt-worker.jar")),
                (javaExecutable, workerClasspath) -> new QuarkusTestApplicationModelWorkerLauncher(
                        ":",
                        javaExecutable,
                        workerClasspath,
                        command -> new QuarkusTestApplicationModelWorkerLauncher.ProcessResult(0, "")));
    }

    private ProjectConfig config(BuildSettings buildSettings) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata(
                                "demo",
                                "1.0.0",
                                "com.example",
                                String.valueOf(Runtime.version().feature()),
                                Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        buildSettings)
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        true,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private void writeBootstrapDescriptor() throws IOException {
        Path quarkusDirectory = projectDir.resolve("target/quarkus");
        Files.createDirectories(quarkusDirectory);
        Path runtimeClasspath = quarkusDirectory.resolve("runtime-classpath.txt");
        Path deploymentClasspath = quarkusDirectory.resolve("deployment-classpath.txt");
        Path applicationModel = quarkusDirectory.resolve("application-model.properties");
        Files.writeString(runtimeClasspath, "");
        Files.writeString(deploymentClasspath, "");
        Files.writeString(applicationModel, """
                version=1
                application.groupId=com.example
                application.artifactId=demo
                application.version=1.0.0
                application.classifier=
                application.type=jar
                application.path=%s
                dependencyCount=0
                """.formatted(projectDir.resolve("target/classes")));
        Files.writeString(quarkusDirectory.resolve("zolt-bootstrap.properties"), """
                version=1
                bootstrapClass=%s
                augmentActionClass=%s
                mode=prod
                package=fast-jar
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                QuarkusBootstrapDescriptorWriter.BOOTSTRAP_CLASS,
                QuarkusBootstrapDescriptorWriter.AUGMENT_ACTION_CLASS,
                projectDir,
                projectDir.resolve("target/classes"),
                quarkusDirectory,
                projectDir.resolve("target/quarkus-app"),
                runtimeClasspath,
                deploymentClasspath,
                applicationModel,
                "sha256:" + "1".repeat(64)));
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
