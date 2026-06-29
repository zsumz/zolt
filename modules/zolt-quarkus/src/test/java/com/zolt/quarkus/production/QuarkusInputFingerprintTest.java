package com.zolt.quarkus.production;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusInputFingerprintTest {
    @TempDir
    private Path tempDir;

    private final QuarkusInputFingerprint fingerprint = new QuarkusInputFingerprint();

    @Test
    void fingerprintsAreStableForSameInputs() throws IOException {
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.writeString(output.resolve("com/example/App.class"), "bytecode-a");

        String first = fingerprint.fingerprint(output, lockfile("runtime-sha", "deployment-sha"));
        String second = fingerprint.fingerprint(output, lockfile("runtime-sha", "deployment-sha"));

        assertEquals(first, second);
        assertTrue(first.matches("sha256:[0-9a-f]{64}"));
    }

    @Test
    void fingerprintChangesWhenApplicationOutputChanges() throws IOException {
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.writeString(output.resolve("com/example/App.class"), "bytecode-a");
        String before = fingerprint.fingerprint(output, lockfile("runtime-sha", "deployment-sha"));

        Files.writeString(output.resolve("com/example/App.class"), "bytecode-b");
        String after = fingerprint.fingerprint(output, lockfile("runtime-sha", "deployment-sha"));

        assertNotEquals(before, after);
    }

    @Test
    void fingerprintChangesWhenArtifactChecksumChanges() throws IOException {
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output);

        String before = fingerprint.fingerprint(output, lockfile("runtime-sha", "deployment-sha"));
        String after = fingerprint.fingerprint(output, lockfile("runtime-sha", "other-deployment-sha"));

        assertNotEquals(before, after);
    }

    private static ZoltLockfile lockfile(String runtimeSha, String deploymentSha) {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        lockPackage(
                                "io.quarkus",
                                "quarkus-rest",
                                DependencyScope.COMPILE,
                                "io/quarkus/quarkus-rest/3.33.0/quarkus-rest-3.33.0.jar",
                                runtimeSha),
                        lockPackage(
                                "io.quarkus",
                                "quarkus-rest-deployment",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                "io/quarkus/quarkus-rest-deployment/3.33.0/quarkus-rest-deployment-3.33.0.jar",
                                deploymentSha)),
                List.<LockConflict>of());
    }

    private static LockPackage lockPackage(
            String groupId,
            String artifactId,
            DependencyScope scope,
            String jar,
            String jarSha256) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                "3.33.0",
                "maven-central",
                scope,
                scope == DependencyScope.COMPILE,
                Optional.of(jar),
                Optional.empty(),
                Optional.of(jarSha256),
                Optional.empty(),
                List.of());
    }
}
