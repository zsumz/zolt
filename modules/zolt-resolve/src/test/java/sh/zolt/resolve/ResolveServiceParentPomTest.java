package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.resolve.support.ResolveServiceTestSupport;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResolveServiceParentPomTest extends ResolveServiceTestSupport {
    @Test
    void parentPomDependenciesAreInheritedIntoRuntimeGraph() {
        addPom("com.example", "services-parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>services-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>services-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                </project>
                """);
        addArtifact("com.example", "runtime-client", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>runtime-client</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project-parent-dependencies");
        Path cacheRoot = tempDir.resolve("cache-parent-dependencies");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = packageFor(lockfile, "com.example", "app");
        assertEquals(List.of("com.example:runtime-client:1.0.0"), app.dependencies());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "runtime-client"))));
    }

    @Test
    void childDependencyOverridesParentDependencyVersion() {
        addPom("com.example", "services-parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>services-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>2.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>services-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "runtime-client", "1.0.0", minimalPom("runtime-client", "1.0.0"));
        addArtifact("com.example", "runtime-client", "2.0.0", minimalPom("runtime-client", "2.0.0"));
        Path projectDir = tempDir.resolve("project-parent-dependency-version-override");
        Path cacheRoot = tempDir.resolve("cache-parent-dependency-version-override");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = packageFor(lockfile, "com.example", "app");
        assertEquals(List.of("com.example:runtime-client:1.0.0"), app.dependencies());
        assertTrue(containsPackageVersion(lockfile, "com.example", "runtime-client", "1.0.0"));
        assertFalse(containsPackageVersion(lockfile, "com.example", "runtime-client", "2.0.0"));
    }

    @Test
    void childDependencyOverridesParentDependencyExclusions() {
        addPom("com.example", "services-parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>services-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>services-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>1.0.0</version>
                      <scope>runtime</scope>
                      <exclusions>
                        <exclusion>
                          <groupId>com.example</groupId>
                          <artifactId>legacy-helper</artifactId>
                        </exclusion>
                      </exclusions>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "runtime-client", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>runtime-client</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>legacy-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "legacy-helper", "1.0.0", minimalPom("legacy-helper", "1.0.0"));
        Path projectDir = tempDir.resolve("project-parent-dependency-exclusion-override");
        Path cacheRoot = tempDir.resolve("cache-parent-dependency-exclusion-override");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = packageFor(lockfile, "com.example", "app");
        assertEquals(List.of("com.example:runtime-client:1.0.0"), app.dependencies());
        assertFalse(containsPackage(lockfile, "com.example", "legacy-helper"));
    }

    @Test
    void nearestParentDependencyOverridesGrandparentDependency() {
        addPom("com.example", "root-parent", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>root-parent</artifactId>
                  <version>1.0.0</version>
                  <packaging>pom</packaging>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>3.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addPom("com.example", "services-parent", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>root-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>services-parent</artifactId>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>runtime-client</artifactId>
                      <version>2.0.0</version>
                      <scope>runtime</scope>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <parent>
                    <groupId>com.example</groupId>
                    <artifactId>services-parent</artifactId>
                    <version>1.0.0</version>
                  </parent>
                  <artifactId>app</artifactId>
                </project>
                """);
        addArtifact("com.example", "runtime-client", "2.0.0", minimalPom("runtime-client", "2.0.0"));
        addArtifact("com.example", "runtime-client", "3.0.0", minimalPom("runtime-client", "3.0.0"));
        Path projectDir = tempDir.resolve("project-parent-dependency-multi-level-override");
        Path cacheRoot = tempDir.resolve("cache-parent-dependency-multi-level-override");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = packageFor(lockfile, "com.example", "app");
        assertEquals(List.of("com.example:runtime-client:2.0.0"), app.dependencies());
        assertTrue(containsPackageVersion(lockfile, "com.example", "runtime-client", "2.0.0"));
        assertFalse(containsPackageVersion(lockfile, "com.example", "runtime-client", "3.0.0"));
    }

    private static LockPackage packageFor(ZoltLockfile lockfile, String groupId, String artifactId) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId(groupId, artifactId)))
                .findFirst()
                .orElseThrow();
    }

    private static boolean containsPackage(ZoltLockfile lockfile, String groupId, String artifactId) {
        return lockfile.packages().stream()
                .anyMatch(lockPackage -> lockPackage.packageId().equals(new PackageId(groupId, artifactId)));
    }

    private static boolean containsPackageVersion(
            ZoltLockfile lockfile,
            String groupId,
            String artifactId,
            String version) {
        return lockfile.packages().stream()
                .anyMatch(lockPackage ->
                        lockPackage.packageId().equals(new PackageId(groupId, artifactId))
                                && lockPackage.version().equals(version));
    }

    private static String minimalPom(String artifactId, String version) {
        return """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>%s</artifactId>
                  <version>%s</version>
                </project>
                """.formatted(artifactId, version);
    }
}
