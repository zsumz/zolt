package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceParallelismTest extends ResolveServiceTestSupport {
    @Test
    void repeatedResolveUsesCachedArtifacts() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult first = resolveService.resolve(projectDir, config(), cacheRoot);
        ResolveResult second = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(4, first.downloadCount());
        assertEquals(0, second.downloadCount());
        assertEquals(4, second.metrics().pomCacheHits() + second.metrics().jarCacheHits());
        assertTrue(first.metrics().pomDownloadNanos() > 0);
        assertTrue(first.metrics().artifactDownloadNanos() > 0);
        assertTrue(second.metrics().pomCacheHitNanos() > 0);
        assertTrue(second.metrics().artifactCacheHitNanos() > 0);
        assertEquals(0, second.metrics().pomDownloadNanos() + second.metrics().artifactDownloadNanos());
        assertTrue(second.metrics().rawPomParseNanos() > 0);
        assertTrue(second.metrics().effectivePomBuildNanos() > 0);
    }

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
    void randomizedRepositoryResponseTimingKeepsLockfileStable() throws IOException {
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
        setResponseDelays(Map.of(
                "alpha", 120L,
                "beta", 10L,
                "gamma", 70L,
                "delta", 30L,
                "epsilon", 90L));
        Path projectDir = tempDir.resolve("project-randomized");
        Path cacheRoot = tempDir.resolve("cache-randomized");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(dependencies);

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        responseDelayMillis.clear();
        setResponseDelays(Map.of(
                "alpha", 5L,
                "beta", 130L,
                "gamma", 25L,
                "delta", 100L,
                "epsilon", 45L));
        Path secondProjectDir = tempDir.resolve("project-randomized-second");
        Path secondCacheRoot = tempDir.resolve("cache-randomized-second");
        createDirectory(secondProjectDir);
        ResolveResult second = resolveService.resolve(secondProjectDir, config, secondCacheRoot);

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

    @Test
    void repeatedParentPomsAreParsedOncePerResolve() {
        addPom("com.example", "parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>parent</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "child-a", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>child-a</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "child-b", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>child-b</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of(
                        "com.example:child-a", "1.0.0",
                        "com.example:child-b", "1.0.0")),
                cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(1, result.metrics().rawPomCacheHits());
        assertTrue(result.metrics().rawPomCacheMisses() >= 3);
        assertTrue(Files.exists(result.lockfilePath()));
    }
}
