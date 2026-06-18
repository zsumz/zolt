package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResolveServiceRelocationTest extends ResolveServiceTestSupport {
    @Test
    void transitiveRelocationWritesFinalCoordinateAndDependencyReferenceToLockfile() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.legacy</groupId>
                      <artifactId>old-lib</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addPom("com.legacy", "old-lib", "1.0.0", """
                <project>
                  <groupId>com.legacy</groupId>
                  <artifactId>old-lib</artifactId>
                  <version>1.0.0</version>
                  <distributionManagement>
                    <relocation>
                      <groupId>com.modern</groupId>
                      <artifactId>new-lib</artifactId>
                      <version>2.0.0</version>
                    </relocation>
                  </distributionManagement>
                </project>
                """);
        addArtifact("com.modern", "new-lib", "2.0.0", simplePom("com.modern", "new-lib", "2.0.0"));
        Path projectDir = tempDir.resolve("project-transitive-relocation");
        Path cacheRoot = tempDir.resolve("cache-transitive-relocation");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, config(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = packageFor(lockfile, "com.example", "app");
        LockPackage relocated = packageFor(lockfile, "com.modern", "new-lib");
        assertEquals(List.of("com.modern:new-lib:2.0.0"), app.dependencies());
        assertEquals("2.0.0", relocated.version());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.legacy", "old-lib"))));
    }

    @Test
    void relocationMissingFieldsInheritsCurrentEffectiveCoordinate() {
        addPom("com.legacy", "old-lib", "1.0.0", """
                <project>
                  <groupId>com.legacy</groupId>
                  <artifactId>old-lib</artifactId>
                  <version>1.0.0</version>
                  <distributionManagement>
                    <relocation>
                      <artifactId>new-lib</artifactId>
                    </relocation>
                  </distributionManagement>
                </project>
                """);
        addArtifact("com.legacy", "new-lib", "1.0.0", simplePom("com.legacy", "new-lib", "1.0.0"));
        Path projectDir = tempDir.resolve("project-inherited-relocation");
        Path cacheRoot = tempDir.resolve("cache-inherited-relocation");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(java.util.Map.of("com.legacy:old-lib", "1.0.0")),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage relocated = packageFor(lockfile, "com.legacy", "new-lib");
        assertEquals("1.0.0", relocated.version());
        assertTrue(relocated.direct());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.legacy", "old-lib"))));
    }

    private static LockPackage packageFor(ZoltLockfile lockfile, String groupId, String artifactId) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId(groupId, artifactId)))
                .findFirst()
                .orElseThrow();
    }
}
