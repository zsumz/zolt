package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceQuarkusFailureTest extends ResolveServiceTestSupport {
    @Test
    void quarkusDeploymentArtifactWithUnsupportedTypeFailsClearly() {
        addArtifact("io.quarkus", "quarkus-custom", "1.0.0", """
                <project>
                  <groupId>io.quarkus</groupId>
                  <artifactId>quarkus-custom</artifactId>
                  <version>1.0.0</version>
                </project>
                """, Map.of(
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-custom-deployment::zip:1.0.0\n"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        quarkusConfigWithDependencies(Map.of("io.quarkus:quarkus-custom", "1.0.0")),
                        cacheRoot));

        assertTrue(exception.getMessage().contains("declares deployment artifact"));
        assertTrue(exception.getMessage().contains("currently supports only jar deployment artifacts"));
    }
}
