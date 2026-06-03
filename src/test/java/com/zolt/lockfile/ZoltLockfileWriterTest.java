package com.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
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

        assertEquals(expected, writer.write(unsortedLockfile()));
    }

    @Test
    void sameInputProducesStableOutput() {
        ZoltLockfile lockfile = unsortedLockfile();

        assertEquals(writer.write(lockfile), writer.write(lockfile));
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
    void writesProcessorScopeNames() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        lockPackage("com.example", "processor", "1.0.0", DependencyScope.PROCESSOR, true, Optional.empty(), Optional.empty(), List.of()),
                        lockPackage("com.example", "test-processor", "1.0.0", DependencyScope.TEST_PROCESSOR, true, Optional.empty(), Optional.empty(), List.of())),
                List.of());

        String output = writer.write(lockfile);

        assertTrue(output.contains("scope = \"processor\""));
        assertTrue(output.contains("scope = \"test-processor\""));
    }

    @Test
    void writesWorkspacePackageFields() {
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(new LockPackage(
                        new PackageId("com.acme", "core"),
                        "0.1.0",
                        "workspace",
                        DependencyScope.COMPILE,
                        true,
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.of("modules/core"),
                        Optional.of("target/classes"),
                        List.of())),
                List.of());

        String output = writer.write(lockfile);

        assertTrue(output.contains("source = \"workspace\""));
        assertTrue(output.contains("workspace = \"modules/core\""));
        assertTrue(output.contains("workspaceOutput = \"target/classes\""));
    }

    private static ZoltLockfile unsortedLockfile() {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
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
                        ConflictSelectionReason.DIRECT_DEPENDENCY)));
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
