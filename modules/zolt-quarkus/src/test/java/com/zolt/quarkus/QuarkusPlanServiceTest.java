package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.zolt.lockfile.toml.LockfileReadException;
import com.zolt.lockfile.ZoltLockfile;
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

final class QuarkusPlanServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void failsBeforePlanningWorkerInputsWhenDeploymentJarDoesNotMatchLockfileHash() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path deploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar");
        Files.createDirectories(deploymentJar.getParent());
        Files.writeString(deploymentJar, "corrupted deployment jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.2"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> new QuarkusPlanService().plan(projectDir, config(), cacheRoot));

        assertTrue(exception.getMessage().contains(
                "Cached jar integrity check failed for io.quarkus:quarkus-rest-deployment:3.33.2"));
    }

    @Test
    void rejectsUnsafeBuildOutputPath() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(
                        projectDir,
                        config(new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                "../outside-classes",
                                "target/test-classes")),
                        emptyLockfile(),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("../outside-classes"));
    }

    @Test
    void rejectsQuarkusOutputSymlinkThatEscapesProject() throws IOException {
        createSymlink(projectDir.resolve("target"), Files.createTempDirectory("zolt-quarkus-target-"));

        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(
                        projectDir,
                        config(new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                "classes",
                                "test-classes")),
                        emptyLockfile(),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Quarkus augmentation output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    private static ProjectConfig config() {
        return config(BuildSettings.defaults());
    }

    private static ProjectConfig config(BuildSettings buildSettings) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        buildSettings)
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        true,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private static ZoltLockfile emptyLockfile() {
        return new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.of(), List.of());
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
