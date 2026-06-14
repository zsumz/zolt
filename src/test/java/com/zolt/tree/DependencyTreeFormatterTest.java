package com.zolt.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.DependencyScope;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyTreeFormatterTest {
    private final DependencyTreeFormatter formatter = new DependencyTreeFormatter();
    private final DependencyJsonFormatter jsonFormatter = new DependencyJsonFormatter();

    @Test
    void formatsDirectAndTransitiveDependenciesDeterministically() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        lockPackage("org.slf4j", "slf4j-api", "2.0.16", false, List.of()),
                        lockPackage("com.google.guava", "guava", "33.4.0-jre", true, List.of(
                                "org.slf4j:slf4j-api:2.0.16",
                                "com.google.guava:failureaccess:1.0.2")),
                        lockPackage("com.google.guava", "failureaccess", "1.0.2", false, List.of())),
                List.of());

        String output = formatter.format(config(), lockfile);

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.google.guava:guava:33.4.0-jre
                   +- com.google.guava:failureaccess:1.0.2
                   \\- org.slf4j:slf4j-api:2.0.16
                """, output);
    }

    @Test
    void marksPackagesWithSelectedConflictVersions() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(lockPackage("org.slf4j", "slf4j-api", "2.0.16", true, List.of())),
                List.of(new LockConflict(
                        new PackageId("org.slf4j", "slf4j-api"),
                        "2.0.16",
                        List.of("1.7.36", "2.0.16"),
                        ConflictSelectionReason.DIRECT_DEPENDENCY)));

        String output = formatter.format(config(), lockfile);

        assertEquals("""
                com.example:demo:0.1.0
                \\- org.slf4j:slf4j-api:2.0.16 (conflict: selected 2.0.16; requested 1.7.36, 2.0.16; direct dependency wins)
                """, output);
    }

    @Test
    void showsPackagePolicies() {
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

        String output = formatter.format(config(), lockfile);

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0 (policy: managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0)
                """, output);
    }

    @Test
    void showsExcludedPolicyEffects() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(lockPackage("com.example", "app", "1.0.0", true, List.of())),
                List.of(),
                List.of(new LockPolicyEffect(
                        "global-exclusion",
                        new PackageId("commons-logging", "commons-logging"),
                        Optional.of("1.2"),
                        Optional.of("com.example:app:1.0.0"),
                        "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)")));

        String output = formatter.format(config(), lockfile);

        assertEquals("""
                com.example:demo:0.1.0
                \\- com.example:app:1.0.0
                Policy effects
                - global-exclusion commons-logging:commons-logging:1.2 from com.example:app:1.0.0: [dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)
                """, output);
    }

    @Test
    void formatsStableJsonWithPolicyEffects() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(lockPackage(
                        "com.example",
                        "app",
                        "1.0.0",
                        true,
                        List.of("com.example:lib:1.0.0"),
                        List.of("managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0")),
                        lockPackage("com.example", "lib", "1.0.0", false, List.of())),
                List.of(new LockConflict(
                        new PackageId("com.example", "lib"),
                        "1.0.0",
                        List.of("0.9.0", "1.0.0"),
                        ConflictSelectionReason.DIRECT_DEPENDENCY)),
                List.of(new LockPolicyEffect(
                        "global-exclusion",
                        new PackageId("commons-logging", "commons-logging"),
                        Optional.of("1.2"),
                        Optional.of("com.example:app:1.0.0"),
                        "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)")));

        String output = jsonFormatter.tree(config(), lockfile);

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "tree",
                  "project": {
                    "group": "com.example",
                    "name": "demo",
                    "version": "0.1.0",
                    "coordinate": "com.example:demo:0.1.0"
                  },
                  "packages": [
                    {
                      "id": "com.example:app",
                      "version": "1.0.0",
                      "coordinate": "com.example:app:1.0.0",
                      "scope": "compile",
                      "direct": true,
                      "dependencies": ["com.example:lib:1.0.0"],
                      "policies": ["managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0"]
                    },
                    {
                      "id": "com.example:lib",
                      "version": "1.0.0",
                      "coordinate": "com.example:lib:1.0.0",
                      "scope": "compile",
                      "direct": false,
                      "dependencies": [],
                      "policies": []
                    }
                  ],
                  "roots": ["com.example:app:1.0.0"],
                  "conflicts": [
                    {
                      "id": "com.example:lib",
                      "selected": "1.0.0",
                      "requested": ["0.9.0", "1.0.0"],
                      "reason": "direct dependency wins"
                    }
                  ],
                  "policyEffects": [
                    {
                      "kind": "global-exclusion",
                      "id": "commons-logging:commons-logging",
                      "requested": "1.2",
                      "source": "com.example:app:1.0.0",
                      "policy": "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                    }
                  ]
                }
                """, output);
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
