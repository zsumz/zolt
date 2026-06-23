package com.zolt.build;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PackageEvidenceChecksums {
    private PackageEvidenceChecksums() {
    }

    static String fileSha256(Path path) {
        if (!Files.isRegularFile(path)) {
            return "missing";
        }
        return sha256(path);
    }

    static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (IOException exception) {
            throw new PackageException(
                    "Could not read package evidence input at "
                            + path
                            + ". Check that the file is readable and retry.",
                    exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PackageException("Could not compute package evidence checksum because SHA-256 is unavailable.", exception);
        }
    }
}
