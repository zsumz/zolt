package sh.zolt.resolve;

import sh.zolt.resolve.support.ResolveServiceTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceParallelismCachingTest extends ResolveServiceTestSupport {
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
