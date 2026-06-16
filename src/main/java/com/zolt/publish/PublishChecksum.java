package com.zolt.publish;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

final class PublishChecksum {
    private PublishChecksum() {
    }

    static String sha256(Path path) {
        try {
            return "sha256:" + HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        } catch (java.io.IOException exception) {
            throw new PublishException("Could not read package artifact at " + path + ".", exception);
        } catch (NoSuchAlgorithmException exception) {
            throw new PublishException(
                    "Could not compute package artifact checksum because SHA-256 is unavailable.",
                    exception);
        }
    }
}
