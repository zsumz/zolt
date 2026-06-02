package com.zolt.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class DependencyTreeFormatterTest {
    private final DependencyTreeFormatter formatter = new DependencyTreeFormatter();

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
}
