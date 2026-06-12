package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.lockfile.LockfileReadException;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
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

    private static ProjectConfig config() {
        return new ProjectConfig(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        true,
                        QuarkusPackageMode.FAST_JAR)));
    }
}
