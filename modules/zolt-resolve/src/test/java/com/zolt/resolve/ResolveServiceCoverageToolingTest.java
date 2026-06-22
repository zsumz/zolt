package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceCoverageToolingTest extends ResolveServiceTestSupport {
    @Test
    void coverageResolveAddsJacocoToolingOnlyScope() {
        addArtifact("com.example", "app", "1.0.0", simplePom("com.example", "app", "1.0.0"));
        addJUnitConsoleArtifact("1.11.4");
        addArtifact("org.jacoco", "org.jacoco.agent", "0.8.14", """
                <project>
                  <groupId>org.jacoco</groupId>
                  <artifactId>org.jacoco.agent</artifactId>
                  <version>0.8.14</version>
                </project>
                """);
        addClassifierJar("org.jacoco", "org.jacoco.agent", "0.8.14", "runtime", Map.of());
        addArtifact("org.jacoco", "org.jacoco.cli", "0.8.14", """
                <project>
                  <groupId>org.jacoco</groupId>
                  <artifactId>org.jacoco.cli</artifactId>
                  <version>0.8.14</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolveWithCoverageTooling(
                projectDir,
                configWithTestDependencies(Map.of("com.example:app", "1.0.0")),
                cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.jacoco", "org.jacoco.agent"))
                        && lockPackage.version().equals("0.8.14")
                        && lockPackage.scope() == DependencyScope.TOOL_COVERAGE
                        && lockPackage.jar().orElseThrow().endsWith("org.jacoco.agent-0.8.14-runtime.jar")));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.jacoco", "org.jacoco.cli"))
                        && lockPackage.version().equals("0.8.14")
                        && lockPackage.scope() == DependencyScope.TOOL_COVERAGE));
    }
}
