package sh.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.classpath.ClasspathBuilder;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.build.classpath.LockfileClasspathPackageConverter;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServiceScopeTest extends ResolveServiceTestSupport {
    @Test
    void directRuntimeAndProvidedDependenciesUseDistinctScopes() {
        addArtifact("com.example", "runtime-tool", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>runtime-tool</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        addArtifact("jakarta.servlet", "jakarta.servlet-api", "6.1.0", """
                <project>
                  <groupId>jakarta.servlet</groupId>
                  <artifactId>jakarta.servlet-api</artifactId>
                  <version>6.1.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, runtimeProvidedConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "runtime-tool"))
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("jakarta.servlet", "jakarta.servlet-api"))
                        && lockPackage.scope() == DependencyScope.PROVIDED
                        && lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(
                cacheRoot.resolve("jakarta/servlet/jakarta.servlet-api/6.1.0/jakarta.servlet-api-6.1.0.jar")),
                classpaths.compile().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/runtime-tool/1.0.0/runtime-tool-1.0.0.jar")),
                classpaths.runtime().entries());
    }

    @Test
    void directDevDependenciesAndTransitivesUseDevScope() {
        addArtifact("com.example", "devtools", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>devtools</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>dev-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "dev-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>dev-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, devConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "devtools"))
                        && lockPackage.scope() == DependencyScope.DEV
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "dev-helper"))
                        && lockPackage.scope() == DependencyScope.DEV
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/dev-helper/1.0.0/dev-helper-1.0.0.jar"),
                cacheRoot.resolve("com/example/devtools/1.0.0/devtools-1.0.0.jar")),
                classpaths.runtime().entries());
    }


}
