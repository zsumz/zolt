package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.classpath.LockfileClasspathPackageConverter;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ResolveServiceGeneratedSourceTest extends ResolveServiceTestSupport {
    @Test
    void annotationProcessorsResolveToProcessorScopesOnly() {
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>processor-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "processor-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, processorConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor"))
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor-helper"))
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(), classpaths.runtime().entries());
        assertEquals(List.of(), classpaths.test().entries());
        assertEquals(List.of(
                cacheRoot.resolve("com/example/processor-helper/1.0.0/processor-helper-1.0.0.jar"),
                cacheRoot.resolve("com/example/processor/1.0.0/processor-1.0.0.jar")),
                classpaths.processor().entries());
        assertEquals(List.of(), classpaths.testProcessor().entries());
    }

    @Test
    void openApiToolResolvesToToolScopeOnly() {
        addArtifact("org.openapitools", "openapi-generator-cli", "7.11.0", """
                <project>
                  <groupId>org.openapitools</groupId>
                  <artifactId>openapi-generator-cli</artifactId>
                  <version>7.11.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>generator-helper</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "generator-helper", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>generator-helper</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, openApiConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.openapitools", "openapi-generator-cli"))
                        && lockPackage.scope() == DependencyScope.TOOL_OPENAPI
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "generator-helper"))
                        && lockPackage.scope() == DependencyScope.TOOL_OPENAPI
                        && !lockPackage.direct()));

        ClasspathSet classpaths = new ClasspathBuilder().build(LockfileClasspathPackageConverter.classpathPackages(lockfile, cacheRoot));
        assertEquals(List.of(), classpaths.compile().entries());
        assertEquals(List.of(), classpaths.runtime().entries());
        assertEquals(List.of(), classpaths.test().entries());
        assertEquals(List.of(), classpaths.processor().entries());
        assertEquals(List.of(), classpaths.testProcessor().entries());
        assertEquals(List.of(), classpaths.quarkusDeployment().entries());
    }

    @Test
    void projectPlatformProvidesManagedVersionForAnnotationProcessor() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>processor</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "processor", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>processor</artifactId>
                  <version>1.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, processorPlatformConfig(), cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "processor"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.scope() == DependencyScope.PROCESSOR
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void unmanagedAnnotationProcessorFailsClearly() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>other</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, processorPlatformConfig(), cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency com.example:processor in [annotationProcessors]"));
        assertTrue(exception.getMessage().contains("uses a platform-managed version"));
    }
}
