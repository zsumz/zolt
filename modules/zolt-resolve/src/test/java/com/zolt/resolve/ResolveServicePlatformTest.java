package com.zolt.resolve;

import com.zolt.resolve.support.ResolveServiceTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

final class ResolveServicePlatformTest extends ResolveServiceTestSupport {
    @Test
    void projectPlatformProvidesManagedVersionForDirectDependency() {
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
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, platformConfig(), cacheRoot);

        assertEquals(2, result.resolvedCount());
        assertEquals(5, result.downloadCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals("1.0.0", app.version());
        assertTrue(app.direct());
        assertEquals(
                List.of("managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0"),
                app.policies());
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "platform"))));
    }

    @Test
    void projectPlatformManagedVersionOverridesDeepTransitiveRequests() {
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
                        <groupId>com.fasterxml.jackson.core</groupId>
                        <artifactId>jackson-databind</artifactId>
                        <version>2.16.2</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "app", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>left</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                    <dependency>
                      <groupId>com.example</groupId>
                      <artifactId>right</artifactId>
                      <version>1.0.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "left", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>left</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.15.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.example", "right", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>right</artifactId>
                  <version>1.0.0</version>
                  <dependencies>
                    <dependency>
                      <groupId>com.fasterxml.jackson.core</groupId>
                      <artifactId>jackson-databind</artifactId>
                      <version>2.17.0</version>
                    </dependency>
                  </dependencies>
                </project>
                """);
        addArtifact("com.fasterxml.jackson.core", "jackson-databind", "2.16.2", """
                <project>
                  <groupId>com.fasterxml.jackson.core</groupId>
                  <artifactId>jackson-databind</artifactId>
                  <version>2.16.2</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project-transitive-platform");
        Path cacheRoot = tempDir.resolve("cache-transitive-platform");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, platformConfig(), cacheRoot);

        assertEquals(4, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage jackson = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(
                        new PackageId("com.fasterxml.jackson.core", "jackson-databind")))
                .findFirst()
                .orElseThrow();
        assertEquals("2.16.2", jackson.version());
        assertEquals(
                List.of("managed-version: com.fasterxml.jackson.core:jackson-databind -> 2.16.2 from com.example:platform:1.0.0"),
                jackson.policies());
        assertTrue(lockfile.policyEffects().stream().anyMatch(effect ->
                "managed-version".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.fasterxml.jackson.core", "jackson-databind"))
                        && effect.requestedVersion().orElseThrow().equals("2.15.0")
                        && effect.source().orElseThrow().equals("com.example:left:1.0.0")));
        assertTrue(lockfile.policyEffects().stream().anyMatch(effect ->
                "managed-version".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.fasterxml.jackson.core", "jackson-databind"))
                        && effect.requestedVersion().orElseThrow().equals("2.17.0")
                        && effect.source().orElseThrow().equals("com.example:right:1.0.0")));
        assertEquals(0, requestCount(pomRepositoryPath(
                "com.fasterxml.jackson.core",
                "jackson-databind",
                "2.17.0")));
    }

    @Test
    void strictConstraintWinsOverProjectPlatformManagedTransitiveVersion() {
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
                        <groupId>com.example</groupId>
                        <artifactId>lib</artifactId>
                        <version>2.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "lib", "3.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>3.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project-strict-over-platform");
        Path cacheRoot = tempDir.resolve("cache-strict-over-platform");
        createDirectory(projectDir);
        ProjectConfig config = platformConfig().withDependencyPolicy(new DependencyPolicySettings(
                List.of(),
                Map.of(
                        "com.example:lib",
                        new DependencyConstraint(
                                "com.example:lib",
                                "3.0.0",
                                DependencyConstraintKind.STRICT,
                                Optional.of("Enterprise baseline")))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        assertEquals(2, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("3.0.0", lib.version());
        assertEquals(
                List.of("strict-version: com.example:lib requested 1.0.0 -> 3.0.0 (Enterprise baseline)"),
                lib.policies());
        assertTrue(lockfile.policyEffects().stream().noneMatch(effect ->
                "managed-version".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.example", "lib"))));
    }

    @Test
    void laterProjectPlatformManagedVersionRecordsSelectedPlatformSource() {
        addPom("com.example", "platform-a", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform-a</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>1.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addPom("com.example", "platform-b", "1.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>platform-b</artifactId>
                  <version>1.0.0</version>
                  <dependencyManagement>
                    <dependencies>
                      <dependency>
                        <groupId>com.example</groupId>
                        <artifactId>app</artifactId>
                        <version>2.0.0</version>
                      </dependency>
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addArtifact("com.example", "app", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>app</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = ProjectConfigs.withRuntimeDependencySections(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("test", baseUri.toString()),
                Map.of(
                        "com.example:platform-b", "1.0.0",
                        "com.example:platform-a", "1.0.0"),
                Map.of(),
                Set.of("com.example:app"),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                null);

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);

        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage app = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "app")))
                .findFirst()
                .orElseThrow();
        assertEquals(1, result.resolvedCount());
        assertEquals("2.0.0", app.version());
        assertEquals(
                List.of("managed-version: com.example:app -> 2.0.0 from com.example:platform-b:1.0.0"),
                app.policies());
    }

    @Test
    void projectPlatformProvidesManagedVersionForDirectTestDependency() {
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
                    </dependencies>
                  </dependencyManagement>
                </project>
                """);
        addJUnitConsoleArtifact("1.11.4");
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);

        ResolveResult result = resolveService.resolve(projectDir, testPlatformConfig(), cacheRoot);

        assertEquals(3, result.resolvedCount());
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))
                        && lockPackage.version().equals("1.0.0")
                        && lockPackage.scope() == DependencyScope.TEST
                        && lockPackage.direct()));
    }

    @Test
    void unmanagedDirectDependencyFailsBeforeFetchingDirectPackagePom() {
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
        Path projectDir = tempDir.resolve("project-platform-diagnostic");
        Path cacheRoot = tempDir.resolve("cache-platform-diagnostic");
        createDirectory(projectDir);

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, platformConfig(), cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency com.example:app in [dependencies]"));
        assertTrue(exception.getMessage().contains("uses a platform-managed version"));
        assertEquals(0, requestCount(pomRepositoryPath("com.example", "app", "1.0.0")));
    }
}
