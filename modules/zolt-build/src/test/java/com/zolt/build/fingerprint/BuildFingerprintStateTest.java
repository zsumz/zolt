package com.zolt.build.fingerprint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildFingerprintStateTest {
    @TempDir
    private Path tempDir;

    @Test
    void formatAndParseRoundTripCachedHashes() throws IOException {
        Path alpha = writeFile("alpha.txt", "alpha");
        Path beta = writeFile("beta.txt", "beta");
        BuildFingerprintCachedFileHash betaHash = BuildFingerprintCachedFileHash.read(beta, "hash-beta");
        BuildFingerprintCachedFileHash alphaHash = BuildFingerprintCachedFileHash.read(alpha, "hash-alpha");

        String formatted = BuildFingerprintState.format(
                "fingerprint",
                Map.of(beta, betaHash, alpha, alphaHash));

        BuildFingerprintState state = BuildFingerprintState.parse(formatted.lines().toList()).orElseThrow();
        assertTrue(state.matchesFingerprint("fingerprint"));
        assertFalse(state.matchesFingerprint("changed"));
        assertEquals("hash-alpha", state.hashIfCurrent(alpha).orElseThrow());
        assertEquals("hash-beta", state.hashIfCurrent(beta).orElseThrow());
        assertTrue(state.hashIfCurrent(tempDir.resolve("missing.txt")).isEmpty());
        assertTrue(formatted.indexOf("hash-alpha") < formatted.indexOf("hash-beta"));
    }

    @Test
    void parseRejectsMalformedState() {
        assertTrue(BuildFingerprintState.parse(List.of()).isEmpty());
        assertTrue(BuildFingerprintState.parse(List.of("version=2", "fingerprintSha256=abc")).isEmpty());
        assertTrue(BuildFingerprintState.parse(List.of("version=1", "fingerprintSha256=")).isEmpty());
        assertTrue(BuildFingerprintState.parse(List.of("version=1", "fingerprintSha256=abc", "other")).isEmpty());
        assertTrue(BuildFingerprintState.parse(List.of(
                "version=1",
                "fingerprintSha256=abc",
                "file\t?\t1\t2\thash")).isEmpty());
        assertTrue(BuildFingerprintState.parse(List.of(
                "version=1",
                "fingerprintSha256=abc",
                "file\tL3RtcA\tnot-a-size\t2\thash")).isEmpty());
    }

    @Test
    void cachedFileHashIsStaleWhenFileChanges() throws IOException {
        Path source = writeFile("source.txt", "old");
        BuildFingerprintCachedFileHash cached = BuildFingerprintCachedFileHash.read(source, "hash-old");

        assertTrue(cached.isCurrent());

        Files.writeString(source, "newer content");

        assertFalse(cached.isCurrent());
    }

    private Path writeFile(String name, String content) throws IOException {
        Path path = tempDir.resolve(name);
        Files.writeString(path, content);
        return path;
    }
}
