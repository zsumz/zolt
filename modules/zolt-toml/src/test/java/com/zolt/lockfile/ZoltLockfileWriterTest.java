package com.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ZoltLockfileWriterTest {
    private final ZoltLockfileWriter writer = new ZoltLockfileWriter();

    @Test
    void writesGoldenLockfile() throws IOException {
        String expected = new String(
                ZoltLockfileWriterTest.class.getResourceAsStream("/golden/zolt-lock-writer.golden").readAllBytes(),
                StandardCharsets.UTF_8);

        assertEquals(expected.stripTrailing(), writer.write(unsortedLockfile()).stripTrailing());
    }

    @Test
    void sameInputProducesStableOutput() {
        ZoltLockfile lockfile = unsortedLockfile();

        assertEquals(writer.write(lockfile), writer.write(lockfile));
    }

    @Test
    void writesAliasFingerprintWhenPresent() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                Optional.of("sha256:alias-inputs"),
                List.of(),
                List.of(),
                List.of());

        assertEquals("""
                version = 1
                aliasFingerprint = "sha256:alias-inputs"

                """, writer.write(lockfile));
    }

    @Test
    void writesProjectResolutionFingerprintWhenPresent() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                Optional.empty(),
                Optional.of("sha256:project-inputs"),
                List.of("repositories=sha256:repo-inputs", "dependencies.compile=sha256:compile-inputs"),
                List.of(),
                List.of(),
                List.of());

        assertEquals("""
                version = 1
                projectResolutionFingerprint = "sha256:project-inputs"
                projectResolutionInputFingerprints = ["dependencies.compile=sha256:compile-inputs", "repositories=sha256:repo-inputs"]

                """, writer.write(lockfile));
    }

    @Test
    void packagesAreSortedDeterministically() {
        String output = writer.write(unsortedLockfile());

        assertTrue(output.indexOf("com.google.guava:failureaccess") < output.indexOf("com.google.guava:guava"));
        assertTrue(output.indexOf("com.google.guava:guava") < output.indexOf("org.slf4j:slf4j-api"));
    }

    @Test
    void conflictsAreSortedDeterministically() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(),
                List.of(
                        conflict("org.slf4j", "slf4j-api"),
                        conflict("com.google.guava", "guava")));

        String output = writer.write(lockfile);

        assertTrue(output.indexOf("com.google.guava:guava") < output.indexOf("org.slf4j:slf4j-api"));
    }

    @Test
    void writesPolicyEffectsDeterministically() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(),
                List.of(),
                List.of(
                        new LockPolicyEffect(
                                "global-exclusion",
                                new PackageId("org.slf4j", "jcl-over-slf4j"),
                                Optional.of("2.0.16"),
                                Optional.of("com.example:app:1.0.0"),
                                "[dependencyPolicy].exclude org.slf4j:jcl-over-slf4j"),
                        new LockPolicyEffect(
                                "global-exclusion",
                                new PackageId("commons-logging", "commons-logging"),
                                Optional.of("1.2"),
                                Optional.of("com.example:app:1.0.0"),
                                "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)")));

        String output = writer.write(lockfile);

        assertTrue(output.indexOf("commons-logging:commons-logging") < output.indexOf("org.slf4j:jcl-over-slf4j"));
        assertTrue(output.contains("""
                [[policy]]
                kind = "global-exclusion"
                id = "commons-logging:commons-logging"
                requested = "1.2"
                source = "com.example:app:1.0.0"
                policy = "[dependencyPolicy].exclude commons-logging:commons-logging (Use jcl-over-slf4j)"
                """));
    }

    private static ZoltLockfile unsortedLockfile() {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                Optional.empty(),
                Optional.of("sha256:project-inputs"),
                List.of("repositories=sha256:repo-inputs", "dependencies.compile=sha256:compile-inputs"),
                List.of(
                        lockPackage("org.slf4j", "slf4j-api", "2.0.16", DependencyScope.RUNTIME, false, Optional.empty(), Optional.empty(), List.of()),
                        lockPackage("com.google.guava", "guava", "33.4.0-jre", DependencyScope.COMPILE, true, Optional.of("jar-checksum"), Optional.of("pom-checksum"), List.of(
                                "org.slf4j:slf4j-api:2.0.16",
                                "com.google.guava:failureaccess:1.0.2")),
                        lockPackage("com.google.guava", "failureaccess", "1.0.2", DependencyScope.COMPILE, false, Optional.empty(), Optional.empty(), List.of())),
                List.of(new LockConflict(
                        new PackageId("org.slf4j", "slf4j-api"),
                        "2.0.16",
                        List.of("2.0.16", "1.7.36"),
                        ConflictSelectionReason.DIRECT_DEPENDENCY)),
                List.of());
    }

    private static LockPackage lockPackage(
            String groupId,
            String artifactId,
            String version,
            DependencyScope scope,
            boolean direct,
            Optional<String> jarSha256,
            Optional<String> pomSha256,
            List<String> dependencies) {
        String base = groupId.replace('.', '/') + "/" + artifactId + "/" + version + "/" + artifactId + "-" + version;
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                scope,
                direct,
                Optional.of(base + ".jar"),
                Optional.of(base + ".pom"),
                jarSha256,
                pomSha256,
                dependencies);
    }

    private static LockConflict conflict(String groupId, String artifactId) {
        return new LockConflict(
                new PackageId(groupId, artifactId),
                "2.0.16",
                List.of("2.0.16", "1.7.36"),
                ConflictSelectionReason.NEWEST_VERSION);
    }
}
