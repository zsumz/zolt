package com.zolt.quarkus;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class QuarkusAugmentationMetadataWriter {
    public void write(Path projectDirectory, String inputFingerprint) {
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusPlanException("Quarkus augmentation metadata requires an input fingerprint.");
        }

        Path metadataPath = projectDirectory
                .resolve(QuarkusAugmentationStateReader.METADATA_PATH)
                .normalize();
        try {
            Files.createDirectories(metadataPath.getParent());
            Files.writeString(metadataPath, """
                    version=1
                    inputFingerprint=%s
                    """.formatted(inputFingerprint), StandardCharsets.UTF_8);
        } catch (IOException exception) {
            throw new QuarkusPlanException(
                    "Could not write Quarkus augmentation metadata at "
                            + metadataPath
                            + ". Check that target/ is writable and try again.");
        }
    }
}
