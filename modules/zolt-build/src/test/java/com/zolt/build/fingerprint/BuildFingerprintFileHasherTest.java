package com.zolt.build.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildFingerprintFileHasherTest {
    private final BuildFingerprintFileHasher hasher = new BuildFingerprintFileHasher();

    @TempDir
    private Path tempDir;

    @Test
    void directoryHashIgnoresLocalBuildFingerprintFiles() throws IOException {
        Path output = tempDir.resolve("target/classes");
        Files.createDirectories(output.resolve("com/example"));
        Files.writeString(output.resolve("com/example/App.class"), "app");

        String beforeMetadata = hasher.fileHash(output, null, null);
        Files.writeString(output.resolve(".zolt-build-main.fingerprint"), "fingerprint");
        Files.writeString(output.resolve(".zolt-build-main.fingerprint.state"), "state");
        String afterMetadata = hasher.fileHash(output, null, null);
        Files.writeString(output.resolve("com/example/Other.class"), "other");
        String afterClass = hasher.fileHash(output, null, null);

        assertEquals(beforeMetadata, afterMetadata);
        assertNotEquals(afterMetadata, afterClass);
    }

    @Test
    void cachedStateAvoidsReadingCurrentFileContent() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "content");
        BuildFingerprintCachedFileHash cached = BuildFingerprintCachedFileHash.read(source, "cached-hash");
        BuildFingerprintState state = new BuildFingerprintState("fingerprint", Map.of(source.toAbsolutePath().normalize(), cached));

        assertEquals("cached-hash", hasher.fileHash(source, state, null));
    }

    @Test
    void staleCachedStateThrowsStateMiss() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "old");
        BuildFingerprintCachedFileHash cached = BuildFingerprintCachedFileHash.read(source, "cached-hash");
        Files.writeString(source, "newer content");
        BuildFingerprintState state = new BuildFingerprintState("fingerprint", Map.of(source.toAbsolutePath().normalize(), cached));

        assertThrows(BuildFingerprintStateMiss.class, () -> hasher.fileHash(source, state, null));
    }

    @Test
    void collectedStateRecordsFreshFileHashes() throws IOException {
        Path source = tempDir.resolve("source.txt");
        Files.writeString(source, "content");
        Map<Path, BuildFingerprintCachedFileHash> collected = new HashMap<>();

        String hash = hasher.fileHash(source, null, collected);

        assertEquals(hash, collected.get(source.toAbsolutePath().normalize()).hash());
    }
}
