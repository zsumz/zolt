package com.zolt.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyWhyFormatterTest {
    private final DependencyWhyFormatter formatter = new DependencyWhyFormatter();

    @Test
    void printsStablePathToTransitivePackage() {
        String output = formatter.format(
                config(),
                lockfile(),
                new PackageId("com.example", "lib"));

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                   \\- com.example:lib:1.0.0
                """, output);
    }

    @Test
    void printsStablePathToDirectPackage() {
        String output = formatter.format(
                config(),
                lockfile(),
                new PackageId("com.example", "app"));

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                """, output);
    }

    @Test
    void showsPoliciesOnPathPackages() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(lockPackage(
                        "com.example",
                        "app",
                        "1.0.0",
                        true,
                        List.of(),
                        List.of("managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0"))),
                List.of());

        String output = formatter.format(config(), lockfile, new PackageId("com.example", "app"));

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0 (policy: managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0)
                """, output);
    }

    @Test
    void missingPackageMessageExplainsNextStep() {
        DependencyWhyException exception = assertThrows(
                DependencyWhyException.class,
                () -> formatter.format(config(), lockfile(), new PackageId("com.example", "missing")));

        assertEquals(
                "Package com.example:missing is not present in zolt.lock. Run `zolt resolve` after adding it or check the package id.",
                exception.getMessage());
    }

    private static ZoltLockfile lockfile() {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        lockPackage("com.example", "app", "1.0.0", true, List.of("com.example:lib:1.0.0")),
                        lockPackage("com.example", "lib", "1.0.0", false, List.of())),
                List.of());
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", "21", Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults());
    }

    private static LockPackage lockPackage(
            String groupId,
            String artifactId,
            String version,
            boolean direct,
            List<String> dependencies) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                DependencyScope.COMPILE,
                direct,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies);
    }

    private static LockPackage lockPackage(
            String groupId,
            String artifactId,
            String version,
            boolean direct,
            List<String> dependencies,
            List<String> policies) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                DependencyScope.COMPILE,
                direct,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                dependencies,
                policies);
    }
}
