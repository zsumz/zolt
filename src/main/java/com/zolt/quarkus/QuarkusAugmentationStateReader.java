package com.zolt.quarkus;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import java.util.Properties;

public final class QuarkusAugmentationStateReader {
    public static final String METADATA_PATH = "target/quarkus/zolt-augmentation.properties";

    public QuarkusAugmentationState read(Path projectDirectory, String currentInputFingerprint) {
        Path metadataPath = projectDirectory.resolve(METADATA_PATH).normalize();
        if (!Files.isRegularFile(metadataPath)) {
            return new QuarkusAugmentationState(
                    metadataPath,
                    QuarkusAugmentationState.Status.MISSING,
                    Optional.empty());
        }

        Properties properties = new Properties();
        try (InputStream input = Files.newInputStream(metadataPath)) {
            properties.load(input);
        } catch (IOException exception) {
            throw new QuarkusPlanException(
                    "Could not read Quarkus augmentation metadata at "
                            + metadataPath
                            + ". Check that the file is readable.");
        }

        String version = properties.getProperty("version");
        String recorded = properties.getProperty("inputFingerprint");
        if (!"1".equals(version) || recorded == null || recorded.isBlank()) {
            throw new QuarkusPlanException(
                    "Invalid Quarkus augmentation metadata at "
                            + metadataPath
                            + ". Remove it and rerun Quarkus augmentation when available.");
        }

        QuarkusAugmentationState.Status status = currentInputFingerprint.equals(recorded)
                ? QuarkusAugmentationState.Status.CURRENT
                : QuarkusAugmentationState.Status.STALE;
        return new QuarkusAugmentationState(metadataPath, status, Optional.of(recorded));
    }
}
