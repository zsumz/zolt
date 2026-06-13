package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkRunResult;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.testkit.CachingJdkChecker;
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
        Path runnerJar = projectDir.resolve("target/quarkus-app/quarkus-run.jar");
        FrameworkRunResult runResult = new FrameworkRunResult(runnerJar, "Quarkus runner " + runnerJar);
        List<String> command = new ArrayList<>();
        RunService service = service(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> {
                    assertEquals(projectDir, actualProjectDirectory);
                    assertEquals(config, actualConfig);
                    assertEquals(cacheRoot, actualCacheRoot);
                    return Optional.of(runResult);
                },
                (actualCommand, outputConsumer) -> {
                    command.addAll(actualCommand);
                    outputConsumer.accept("started\n");
                    return new JavaRunner.ProcessResult(0, "started\n");
                });

        RunResult result = service.run(projectDir, config, cacheRoot, List.of("one", "two"), ignored -> {
        });

        assertTrue(command.contains("-jar"));
        assertTrue(command.contains(runnerJar.toString()));
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
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Files.createDirectories(runtimeJar.getParent());
        Files.writeString(runtimeJar, "runtime jar placeholder");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                dependencies = []
                """);
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
        assertTrue(command.stream().anyMatch(entry -> entry.contains(runtimeJar.toString())));
        assertTrue(command.contains("com.example.Main"));
        assertEquals("com.example.Main", result.javaRunResult().mainClass());
    }

    @Test
    void failsBeforeLaunchingApplicationWhenCachedRuntimeJarDoesNotMatchLockfileHash() throws IOException {
        Path projectDir = tempDir.resolve("demo-corrupted-cache");
        Path cacheRoot = tempDir.resolve("cache-corrupted");
        Files.createDirectories(projectDir);
        Path runtimeJar = cacheRoot.resolve("com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar");
        Files.createDirectories(runtimeJar.getParent());
        Files.writeString(runtimeJar, "corrupted runtime jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "com.example:runtime-lib"
                version = "1.0.0"
                source = "maven-central"
                scope = "runtime"
                direct = true
                jar = "com/example/runtime-lib/1.0.0/runtime-lib-1.0.0.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);
        ProjectConfig config = config(false, Optional.of("com.example.Main"));
        RunService service = service(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> Optional.empty(),
                (actualCommand, outputConsumer) -> {
                    throw new AssertionError("application JVM should not be launched with a corrupted cached jar");
                });

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> service.run(projectDir, config, cacheRoot, List.of()));

        assertTrue(exception.getMessage().contains(
                "Cached jar integrity check failed for com.example:runtime-lib:1.0.0"));
    }

    @Test
    void sharesCachedJdkDetectionAcrossBuildAndLaunch() throws IOException {
        Path projectDir = tempDir.resolve("demo");
        Path cacheRoot = tempDir.resolve("cache");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        ProjectConfig config = config(false, Optional.of("com.example.Main"));
        CachingJdkChecker jdkChecker = new CachingJdkChecker();
        RunService service = service(
                (actualProjectDirectory, actualConfig, actualCacheRoot) -> Optional.empty(),
                (actualCommand, outputConsumer) -> new JavaRunner.ProcessResult(0, "hello\n"),
                jdkChecker);

        service.run(projectDir, config, cacheRoot, List.of());

        assertEquals(2, jdkChecker.detectCalls());
        assertEquals(1, jdkChecker.toolchainReads());
    }

    private static RunService service(
            FrameworkRunAugmenter frameworkRunAugmenter,
            JavaRunner.ProcessRunner processRunner) {
        return service(frameworkRunAugmenter, processRunner, new JdkDetector());
    }

    private static RunService service(
            FrameworkRunAugmenter frameworkRunAugmenter,
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker) {
        return new RunService(
                new BuildService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                frameworkRunAugmenter);
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

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        if (version.startsWith("1.")) {
            return version.substring(2, 3);
        }
        int dot = version.indexOf('.');
        return dot >= 0 ? version.substring(0, dot) : version;
    }
}
