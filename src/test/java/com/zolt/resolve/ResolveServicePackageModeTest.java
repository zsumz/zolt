package com.zolt.resolve;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import java.nio.file.Path;
import java.util.Map;
import org.junit.jupiter.api.Test;

final class ResolveServicePackageModeTest extends ResolveServiceTestSupport {
    @Test
    void springBootPackageModeAddsLoaderFromProjectPlatform() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                      <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-loader</artifactId>
                        <version>4.0.6</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("org.springframework.boot", "spring-boot-loader", "4.0.6", """
                <project>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-loader</artifactId>
                  <version>4.0.6</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, springBootPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.version().equals("4.0.6")
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && !lockPackage.direct()));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void springBootWarPackageModeAddsLoaderFromProjectPlatform() {
        addPom("com.example", "platform", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                      <dependency>
                        <groupId>org.springframework.boot</groupId>
                        <artifactId>spring-boot-loader</artifactId>
                        <version>4.0.6</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("org.springframework.boot", "spring-boot-loader", "4.0.6", """
                <project>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-loader</artifactId>
                  <version>4.0.6</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, springBootWarPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.version().equals("4.0.6")
                        && lockPackage.scope() == DependencyScope.RUNTIME
                        && !lockPackage.direct()));
    }

    @Test
    void springBootPackageModeKeepsExplicitLoaderDependencyDirect() {
        addArtifact("org.springframework.boot", "spring-boot-loader", "4.0.6", """
                <project>
                  <groupId>org.springframework.boot</groupId>
                  <artifactId>spring-boot-loader</artifactId>
                  <version>4.0.6</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(
                projectDir,
                configWithDependencies(Map.of("org.springframework.boot:spring-boot-loader", "4.0.6"))
                        .withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                cacheRoot);

        assertEquals(1, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("org.springframework.boot", "spring-boot-loader"))
                        && lockPackage.scope() == DependencyScope.COMPILE
                        && lockPackage.direct()));
    }

    @Test
    void springBootPackageModeFailsClearlyWithoutManagedLoaderVersion() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        config().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                        cacheRoot));

        assertTrue(exception.getMessage().contains("Spring Boot package mode requires package tool artifact"));
        assertTrue(exception.getMessage().contains("Add the Spring Boot platform to [platforms]"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
    }

    @Test
    void springBootWarPackageModeFailsClearlyWithoutManagedLoaderVersion() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(
                        projectDir,
                        config().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT_WAR)),
                        cacheRoot));

        assertTrue(exception.getMessage().contains("Spring Boot package mode requires package tool artifact"));
        assertTrue(exception.getMessage().contains("Add the Spring Boot platform to [platforms]"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
    }
}
