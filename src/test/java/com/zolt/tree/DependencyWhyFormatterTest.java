package com.zolt.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyWhyFormatterTest {
    private final DependencyWhyFormatter formatter = new DependencyWhyFormatter();
    private final DependencyJsonFormatter jsonFormatter = new DependencyJsonFormatter();

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

    @Test
    void explainsPackageAbsentBecausePolicyExcludedIt() {
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

        String output = formatter.format(config(), lockfile, new PackageId("commons-logging", "commons-logging"));

        assertEquals("""
                com.example:demo:0.1.0
                \\- commons-logging:commons-logging (excluded by dependency policy)
                   \\- global-exclusion requested 1.2 from com.example:app:1.0.0: [dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)
                """, output);
    }

    @Test
    void formatsStableJsonForPresentPackage() {
        String output = jsonFormatter.why(config(), lockfile(), new PackageId("com.example", "lib"));

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "why",
                  "project": {
                    "group": "com.example",
                    "name": "demo",
                    "version": "0.1.0",
                    "coordinate": "com.example:demo:0.1.0"
                  },
                  "target": "com.example:lib",
                  "status": "present",
                  "path": [
                    {
                      "id": "com.example:app",
                      "version": "1.0.0",
                      "coordinate": "com.example:app:1.0.0",
                      "scope": "compile",
                      "direct": true,
                      "dependencies": ["com.example:lib:1.0.0"],
                      "policies": []
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
                  "policyEffects": []
                }
                """, output);
    }

    @Test
    void formatsStableJsonForExcludedPackage() {
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

        String output = jsonFormatter.why(config(), lockfile, new PackageId("commons-logging", "commons-logging"));

        assertEquals("""
                {
                  "schemaVersion": 1,
                  "command": "why",
                  "project": {
                    "group": "com.example",
                    "name": "demo",
                    "version": "0.1.0",
                    "coordinate": "com.example:demo:0.1.0"
                  },
                  "target": "commons-logging:commons-logging",
                  "status": "excluded",
                  "path": [],
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

    private static ZoltLockfile lockfile() {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        lockPackage("com.example", "app", "1.0.0", true, List.of("com.example:lib:1.0.0")),
                        lockPackage("com.example", "lib", "1.0.0", false, List.of())),
                List.of());
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
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
