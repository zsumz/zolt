package com.zolt.lockfile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Map;
import java.util.Optional;

public final class ArtifactIntegrityVerifier {
    private final Map<Path, String> verifiedHashes = new HashMap<>();

    public void verify(ZoltLockfile lockfile, Path cacheRoot) {
        Path normalizedCacheRoot = cacheRoot.toAbsolutePath().normalize();
        for (LockPackage lockPackage : lockfile.packages()) {
            verifyArtifact(
                    lockPackage,
                    normalizedCacheRoot,
                    "jar",
                    lockPackage.jar(),
                    lockPackage.jarSha256());
            verifyArtifact(
                    lockPackage,
                    normalizedCacheRoot,
                    "pom",
                    lockPackage.pom(),
                    lockPackage.pomSha256());
            verifyArtifact(
                    lockPackage,
                    normalizedCacheRoot,
                    lockPackage.artifactType().orElse("artifact"),
                    lockPackage.artifact(),
                    lockPackage.artifactSha256());
        }
    }

    private void verifyArtifact(
            LockPackage lockPackage,
            Path cacheRoot,
            String kind,
            Optional<String> relativePath,
            Optional<String> expectedHash) {
        if (relativePath.isEmpty() || expectedHash.isEmpty()) {
            return;
        }
        Path artifactPath = cacheRoot.resolve(relativePath.orElseThrow()).normalize();
        String expected = expectedHash.orElseThrow();
        String actual = verifiedHashes.get(artifactPath);
        if (actual == null) {
            actual = hash(artifactPath, lockPackage, kind, expected);
            verifiedHashes.put(artifactPath, actual);
        }
        if (!expected.equals(actual)) {
            throw mismatch(lockPackage, kind, artifactPath, expected, actual);
        }
    }

    private static String hash(
            Path artifactPath,
            LockPackage lockPackage,
            String kind,
            String expected) {
        if (!Files.isRegularFile(artifactPath)) {
            throw mismatch(lockPackage, kind, artifactPath, expected, "missing file");
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(artifactPath)));
        } catch (IOException exception) {
            throw new LockfileReadException(
                    "Could not verify cached "
                            + kind
                            + " for "
                            + coordinate(lockPackage)
                            + " at "
                            + artifactPath
                            + ". Check that the cache entry is readable, or remove it and run `zolt resolve`.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private static LockfileReadException mismatch(
            LockPackage lockPackage,
            String kind,
            Path artifactPath,
            String expected,
            String actual) {
        return new LockfileReadException(
                "Cached "
                        + kind
                        + " integrity check failed for "
                        + coordinate(lockPackage)
                        + " at "
                        + artifactPath
                        + ". Expected "
                        + expected
                        + " but found "
                        + actual
                        + ". Remove the cache entry or run `zolt resolve` to download it again.");
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }
}
