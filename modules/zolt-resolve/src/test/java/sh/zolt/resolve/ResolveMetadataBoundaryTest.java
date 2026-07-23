package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.resolve.support.ResolveServiceTestSupport;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Boundary guard for the advisory-only version-discovery contract: resolution must never fetch
 * {@code maven-metadata.xml}. Version listings only enter a build when a discovered version is
 * written into zolt.toml as a fixed literal and re-resolved.
 */
final class ResolveMetadataBoundaryTest extends ResolveServiceTestSupport {
    @Test
    void resolveNeverFetchesMavenMetadata() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        resolveService.resolve(projectDir, configWithDependencies(Map.of("com.example:app", "1.0.0")), cacheRoot);

        assertTrue(totalRequests.get() > 0, "expected the resolve to issue repository requests");
        assertTrue(
                requestCounts.keySet().stream().noneMatch(path -> path.contains("maven-metadata.xml")),
                "resolution must not fetch maven-metadata.xml; requested paths: " + requestCounts.keySet());
    }
}
