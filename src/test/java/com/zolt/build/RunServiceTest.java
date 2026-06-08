package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.doctor.JdkDetector;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.quarkus.QuarkusApplicationArtifact;
import com.zolt.quarkus.QuarkusAugmentationResult;
import com.zolt.quarkus.QuarkusBootstrapDescriptor;
import com.zolt.quarkus.QuarkusBootstrapWorkerResult;
import com.zolt.resolve.PackageId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class RunServiceTest {
    @TempDir
    private Path tempDir;

    @Test
    void quarkusRunUsesAugmentedRunnerJarWithoutProjectMainClass() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        ProjectConfig config = config(true, Optional.empty());
        QuarkusAugmentationResult augmentationResult = augmentationResult(projectDir);
        List<String> command = new ArrayList<>();
        RunService service = service(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> {
                    assertEquals(projectDir, actualProjectDirectory);
                    assertEquals(config, actualConfig);
                    assertEquals(cacheRoot, actualCacheRoot);
                    return Optional.of(augmentationResult);
                },
                (actualCommand, outputConsumer) -> {
                    command.addAll(actualCommand);
                    outputConsumer.accept("started\n");
                    return new JavaRunner.ProcessResult(0, "started\n");
                });

        RunResult result = service.run(projectDir, config, cacheRoot, List.of("one", "two"), ignored -> {
        });

        assertTrue(command.contains("-jar"));
        assertTrue(command.contains(augmentationResult.workerResult().runnerJar().toString()));
        assertEquals("one", command.get(command.size() - 2));
        assertEquals("two", command.get(command.size() - 1));
        assertTrue(result.javaRunResult().mainClass().startsWith("Quarkus runner "));
        assertEquals("started\n", result.javaRunResult().output());
    }

    @Test
    void normalRunStillUsesConfiguredMainClassClasspath() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        ProjectConfig config = config(false, Optional.of("com.example.Main"));
        List<String> command = new ArrayList<>();
        RunService service = service(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> Optional.empty(),
                (actualCommand, outputConsumer) -> {
                    command.addAll(actualCommand);
                    return new JavaRunner.ProcessResult(0, "hello\n");
                });

        RunResult result = service.run(projectDir, config, cacheRoot, List.of());

        assertTrue(command.contains("-classpath"));
        assertTrue(command.contains("com.example.Main"));
        assertEquals("com.example.Main", result.javaRunResult().mainClass());
    }

    private static RunService service(
            RunService.QuarkusBuildAugmenter quarkusBuildAugmenter,
            JavaRunner.ProcessRunner processRunner) {
        return new RunService(
                new BuildService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(":", processRunner),
                quarkusBuildAugmenter);
    }

    private static ProjectConfig config(boolean quarkusEnabled, Optional<String> mainClass) {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "1.0.0", "com.example", currentJavaMajorVersion(), mainClass),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        quarkusEnabled,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private static QuarkusAugmentationResult augmentationResult(Path projectDir) {
        Path augmentationDirectory = projectDir.resolve("target/quarkus");
        Path packageDirectory = projectDir.resolve("target/quarkus-app");
        String inputFingerprint = "sha256:" + "1".repeat(64);
        QuarkusBootstrapWorkerResult workerResult = new QuarkusBootstrapWorkerResult(
                inputFingerprint,
                packageDirectory,
                packageDirectory.resolve("quarkus-run.jar"),
                packageDirectory.resolve("lib"),
                1);
        return new QuarkusAugmentationResult(
                augmentationDirectory,
                augmentationDirectory.resolve("zolt-augmentation.properties"),
                descriptor(projectDir, augmentationDirectory, packageDirectory, inputFingerprint),
                inputFingerprint,
                workerResult);
    }

    private static QuarkusBootstrapDescriptor descriptor(
            Path projectDir,
            Path augmentationDirectory,
            Path packageDirectory,
            String inputFingerprint) {
        return new QuarkusBootstrapDescriptor(
                augmentationDirectory.resolve("zolt-bootstrap.properties"),
                augmentationDirectory.resolve("runtime-classpath.txt"),
                augmentationDirectory.resolve("deployment-classpath.txt"),
                augmentationDirectory.resolve("platform-properties.txt"),
                augmentationDirectory.resolve("application-model.properties"),
                "io.quarkus.bootstrap.app.QuarkusBootstrap",
                "io.quarkus.bootstrap.app.AugmentAction",
                projectDir,
                projectDir.resolve("target/classes"),
                augmentationDirectory,
                packageDirectory,
                "fast-jar",
                inputFingerprint,
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        projectDir.resolve("target/classes")),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return version.substring(2, 3);
        }
        int dot = version.indexOf('.');
        return dot >= 0 ? version.substring(0, dot) : version;
    }
}
