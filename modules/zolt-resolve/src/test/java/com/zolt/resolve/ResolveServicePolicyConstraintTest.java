package com.zolt.resolve;

import com.zolt.resolve.support.ResolveServiceTestSupport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ResolveServicePolicyConstraintTest extends ResolveServiceTestSupport {
    @Test
    void dependencyPolicyExcludesMatchingTransitives() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(new DependencyPolicyExclusion(
                        "com.example",
                        "lib",
                        Optional.of("Use the internal logging bridge instead"))),
                Map.of()));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());

        assertEquals(1, result.resolvedCount());
        assertTrue(lockfile.packages().stream().anyMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "app"))));
        assertTrue(lockfile.packages().stream().noneMatch(lockPackage ->
                lockPackage.packageId().equals(new PackageId("com.example", "lib"))));
        LockPolicyEffect effect = lockfile.policyEffects().stream()
                .filter(policyEffect -> policyEffect.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();
        assertEquals("global-exclusion", effect.kind());
        assertEquals("1.0.0", effect.requestedVersion().orElseThrow());
        assertEquals("com.example:app:1.0.0", effect.source().orElseThrow());
        assertEquals("[dependencyPolicy].exclude com.example:lib (Use the internal logging bridge instead)", effect.policy());
    }

    @Test
    void strictDependencyConstraintSelectsTransitiveVersionAndRecordsPolicy() {
        addArtifact("com.example", "lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(),
                Map.of(
                        "com.example:lib",
                        new DependencyConstraint(
                                "com.example:lib",
                                "2.0.0",
                                DependencyConstraintKind.STRICT,
                                Optional.of("Enterprise baseline")))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();

        assertEquals(2, result.resolvedCount());
        assertEquals("2.0.0", lib.version());
        assertEquals(
                List.of("strict-version: com.example:lib requested 1.0.0 -> 2.0.0 (Enterprise baseline)"),
                lib.policies());
        assertTrue(lockfile.policyEffects().stream().anyMatch(effect ->
                "strict-version".equals(effect.kind())
                        && effect.packageId().equals(new PackageId("com.example", "lib"))
                        && effect.requestedVersion().orElseThrow().equals("1.0.0")
                        && effect.source().orElseThrow().equals("com.example:app:1.0.0")
                        && effect.policy().equals("strict-version: com.example:lib requested 1.0.0 -> 2.0.0 (Enterprise baseline)")));
    }

    @Test
    void directDependencyWinsOverStrictTransitiveConstraint() {
        addArtifact("com.example", "lib", "2.0.0", """
                <project>
                  <groupId>com.example</groupId>
                  <artifactId>lib</artifactId>
                  <version>2.0.0</version>
                </project>
                """);
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = configWithDependencies(Map.of(
                        "com.example:app", "1.0.0",
                        "com.example:lib", "1.0.0"))
                .withDependencyPolicy(new DependencyPolicySettings(
                        List.of(),
                        Map.of(
                                "com.example:lib",
                                new DependencyConstraint(
                                        "com.example:lib",
                                        "2.0.0",
                                        DependencyConstraintKind.STRICT,
                                        Optional.empty()))));

        ResolveResult result = resolveService.resolve(projectDir, config, cacheRoot);
        ZoltLockfile lockfile = lockfileReader.read(result.lockfilePath());
        LockPackage lib = lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.packageId().equals(new PackageId("com.example", "lib")))
                .findFirst()
                .orElseThrow();

        assertEquals("1.0.0", lib.version());
        assertTrue(lib.direct());
        assertTrue(lib.policies().isEmpty());
        assertEquals(1, result.conflictCount());
    }

    @Test
    void dependencyPolicyRejectsExcludedDirectDependency() {
        Path projectDir = tempDir.resolve("project");
        Path cacheRoot = tempDir.resolve("cache");
        createDirectory(projectDir);
        ProjectConfig config = config().withDependencyPolicy(new DependencyPolicySettings(
                List.of(new DependencyPolicyExclusion(
                        "com.example",
                        "app",
                        Optional.of("Application artifact is banned in this policy"))),
                Map.of()));

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> resolveService.resolve(projectDir, config, cacheRoot));

        assertTrue(exception.getMessage().contains("Dependency policy excludes direct dependency `com.example:app`"));
        assertTrue(exception.getMessage().contains("Application artifact is banned in this policy"));
        assertTrue(exception.getMessage().contains("Remove the direct dependency or remove the matching [dependencyPolicy].exclude entry"));
        assertEquals(0, totalRequests.get());
    }
}
