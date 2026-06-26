package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceParallelismTest extends ResolveServiceTestSupport {
    @Test
    void selectedArtifactsAreMaterializedConcurrentlyAfterGraphSelection() throws IOException {
        addArtifact("com.example", "alpha", "1.0.0", simplePom("com.example", "alpha", "1.0.0"));
        addArtifact("com.example", "beta", "1.0.0", simplePom("com.example", "beta", "1.0.0"));
        addArtifact("com.example", "gamma", "1.0.0", simplePom("com.example", "gamma", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "alpha", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "beta", "1.0.0"));
        slowArtifactPaths.add(jarRepositoryPath("com.example", "gamma", "1.0.0"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:alpha", "1.0.0");
        dependencies.put("com.example:beta", "1.0.0");
        dependencies.put("com.example:gamma", "1.0.0");
        ProjectConfig config = configWithDependencies(dependencies);

        ResolveResult result = resolveService.resolve(
                projectDir,
                config,
                cacheRoot);

        assertEquals(3, result.resolvedCount());
        assertTrue(maxArtifactRequests.get() > 1);
        assertTrue(maxArtifactRequests.get() <= 4);

        Path secondProjectDir = tempDir.resolve("project-second");
        Path secondCacheRoot = tempDir.resolve("cache-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void pomFrontierMetadataIsFetchedConcurrentlyWithStableLockfile() throws IOException {
        addArtifact("com.example", "frontier-root", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>frontier-root</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>alpha</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>beta</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>gamma</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "alpha", "1.0.0", simplePom("com.example", "alpha", "1.0.0"));
        addArtifact("com.example", "beta", "1.0.0", simplePom("com.example", "beta", "1.0.0"));
        addArtifact("com.example", "gamma", "1.0.0", simplePom("com.example", "gamma", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "alpha", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "beta", "1.0.0"));
        slowPomPaths.add(pomRepositoryPath("com.example", "gamma", "1.0.0"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(Map.of("com.example:frontier-root", "1.0.0"));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        assertEquals(4, result.resolvedCount());
        assertTrue(maxPomRequests.get() > 1);
        assertTrue(maxPomRequests.get() <= 4);

        Path secondProjectDir = tempDir.resolve("project-second");
        Path secondCacheRoot = tempDir.resolve("cache-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void differentDependencyInputOrderKeepsLockfileStable() throws IOException {
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:alpha", "1.0.0");
        dependencies.put("com.example:beta", "1.0.0");
        dependencies.put("com.example:gamma", "1.0.0");
        dependencies.put("com.example:delta", "1.0.0");
        dependencies.put("com.example:epsilon", "1.0.0");
        dependencies.keySet().forEach(coordinate -> {
            String artifactId = coordinate.substring(coordinate.indexOf(':') + 1);
            addArtifact("com.example", artifactId, "1.0.0", simplePom("com.example", artifactId, "1.0.0"));
        });
        Path projectDir = tempDir.resolve("project-input-order");
        Path cacheRoot = tempDir.resolve("cache-input-order");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(dependencies);

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        Map<String, String> reorderedDependencies = new java.util.LinkedHashMap<>();
        reorderedDependencies.put("com.example:epsilon", "1.0.0");
        reorderedDependencies.put("com.example:delta", "1.0.0");
        reorderedDependencies.put("com.example:gamma", "1.0.0");
        reorderedDependencies.put("com.example:beta", "1.0.0");
        reorderedDependencies.put("com.example:alpha", "1.0.0");
        Path secondProjectDir = tempDir.resolve("project-input-order-second");
        Path secondCacheRoot = tempDir.resolve("cache-input-order-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(
                secondProjectDir,
                configWithDependencies(reorderedDependencies),
                secondCacheRoot);

        assertEquals(Files.readString(result.lockfilePath()), Files.readString(second.lockfilePath()));
    }

    @Test
    void parallelArtifactDownloadFailuresAreReportedInSortedOrder() {
        addPom("com.example", "zeta-missing", "1.0.0", simplePom("com.example", "zeta-missing", "1.0.0"));
        addPom("com.example", "alpha-missing", "1.0.0", simplePom("com.example", "alpha-missing", "1.0.0"));
        Path projectDir = tempDir.resolve("project-missing-artifacts");
        Path cacheRoot = tempDir.resolve("cache-missing-artifacts");
        createDirectory(projectDir);
        Map<String, String> dependencies = new java.util.LinkedHashMap<>();
        dependencies.put("com.example:zeta-missing", "1.0.0");
        dependencies.put("com.example:alpha-missing", "1.0.0");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, configWithDependencies(dependencies), cacheRoot));

        assertTrue(exception.getMessage().contains("Selected artifact downloads failed:"));
        assertTrue(exception.getMessage().indexOf("com.example:alpha-missing:1.0.0")
                < exception.getMessage().indexOf("com.example:zeta-missing:1.0.0"));
        assertTrue(exception.getMessage().contains("Retry the command or check your repository and network settings."));
    }

}
