package com.zolt.lockfile;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyScope;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ArtifactIntegrityVerifierTest {
    @TempDir
    private Path tempDir;

    private final ArtifactIntegrityVerifier verifier = new ArtifactIntegrityVerifier();

    @Test
    void acceptsCachedArtifactsThatMatchLockfileHashes() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        Path jar = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.jar"), "jar bytes");
        Path pom = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.pom"), "pom bytes");
        Path artifact = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.properties"), "properties bytes");

        assertDoesNotThrow(() -> verifier.verify(lockfile(
                relative(cacheRoot, jar),
                sha256(jar),
                relative(cacheRoot, pom),
                sha256(pom),
                relative(cacheRoot, artifact),
                sha256(artifact)), cacheRoot));
    }

    @Test
    void rejectsCachedJarThatDoesNotMatchLockfileHash() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        Path jar = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.jar"), "actual jar bytes");

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> verifier.verify(lockfile(
                        relative(cacheRoot, jar),
                        sha256("expected jar bytes"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()), cacheRoot));

        assertTrue(exception.getMessage().contains("Cached jar integrity check failed for com.example:demo:1.0.0"));
        assertTrue(exception.getMessage().contains("Expected " + sha256("expected jar bytes").orElseThrow()));
        assertTrue(exception.getMessage().contains("but found " + sha256(jar).orElseThrow()));
        assertTrue(exception.getMessage().contains("Remove the cache entry or run `zolt resolve`"));
    }

    @Test
    void rejectsMissingCachedArtifactWithRemediation() {
        Path cacheRoot = tempDir.resolve("cache");

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> verifier.verify(lockfile(
                        Optional.of("com/example/demo/1.0.0/demo-1.0.0.jar"),
                        sha256("expected jar bytes"),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty(),
                        Optional.empty()), cacheRoot));

        assertTrue(exception.getMessage().contains("but found missing file"));
        assertTrue(exception.getMessage().contains("Remove the cache entry or run `zolt resolve`"));
    }

    @Test
    void cachesSuccessfulVerificationForCommandInvocation() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        Path jar = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.jar"), "original jar bytes");
        ZoltLockfile lockfile = lockfile(
                relative(cacheRoot, jar),
                sha256(jar),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty());

        verifier.verify(lockfile, cacheRoot);
        Files.writeString(jar, "changed after verification", StandardCharsets.UTF_8);

        assertDoesNotThrow(() -> verifier.verify(lockfile, cacheRoot));
    }

    @Test
    void skipsArtifactsWithoutRecordedChecksums() throws IOException {
        Path cacheRoot = tempDir.resolve("cache");
        Path jar = write(cacheRoot.resolve("com/example/demo/1.0.0/demo-1.0.0.jar"), "jar bytes");

        assertDoesNotThrow(() -> verifier.verify(lockfile(
                relative(cacheRoot, jar),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty()), cacheRoot));
    }

    private static ZoltLockfile lockfile(
            Optional<String> jar,
            Optional<String> jarSha256,
            Optional<String> pom,
            Optional<String> pomSha256,
            Optional<String> artifact,
            Optional<String> artifactSha256) {
        return new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(new LockPackage(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        "maven-central",
                        DependencyScope.COMPILE,
                        true,
                        jar,
                        pom,
                        jarSha256,
                        pomSha256,
                        artifact,
                        artifact.map(ignored -> "properties"),
                        artifactSha256,
                        List.of())),
                List.of());
    }

    private static Path write(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
        return path;
    }

    private static Optional<String> relative(Path root, Path path) {
        return Optional.of(root.relativize(path).toString().replace('\\', '/'));
    }

    private static Optional<String> sha256(Path path) throws IOException {
        return sha256(Files.readString(path, StandardCharsets.UTF_8));
    }

    private static Optional<String> sha256(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return Optional.of(HexFormat.of().formatHex(
                    digest.digest(content.getBytes(StandardCharsets.UTF_8))));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
