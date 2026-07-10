package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private static LockPackage packageFor(ZoltLockfile lockfile, String groupId, String artifactId) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId(groupId, artifactId)))
                .findFirst()
                .orElseThrow();
    }
}
