package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.build.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.build.classpath.LockfileClasspathPackageConverter;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceGraphSelectionTest extends ResolveServiceTestSupport {
    @Test
    void selectedVersionKeepsAllParticipatingScopes() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>shared</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>shared</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "shared", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>shared</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("com.example", "shared", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>shared</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, configWithDependencyAndProcessor(), cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "shared"))
                        && lockPackage.version().equals("2.0.0")
                        && lockPackage.scope() == DependencyScope.COMPILE));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "shared"))
                        && lockPackage.version().equals("2.0.0")
                        && lockPackage.scope() == DependencyScope.PROCESSOR));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertTrue(classpaths.processor().entries().contains(
                cacheRoot.resolve("com/example/shared/2.0.0/shared-2.0.0.jar")));
    }

    @Test
    void selectedGraphClosureUsesDependenciesFromSelectedVersion() {
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>upgrade-path</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "upgrade-path", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>upgrade-path</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>lib</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>2.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>helper</artifactId>
                      <version>2.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "helper", "1.0.0", simplePom("com.example", "helper", "1.0.0"));
        addArtifact("com.example", "helper", "2.0.0", simplePom("com.example", "helper", "2.0.0"));
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of("com.example:app", "1.0.0")),
                cacheRoot);

        assertEquals(2, result.conflictCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("2.0.0", lib.version());
        assertEquals(List.of("com.example:helper:2.0.0"), lib.dependencies());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "helper"))
                        && lockPackage.version().equals("1.0.0")));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertTrue(classpaths.compile().entries().contains(
                cacheRoot.resolve("com/example/helper/2.0.0/helper-2.0.0.jar")));
    }
}
