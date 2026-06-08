package com.zolt.quarkus;

import java.nio.file.Path;

public record QuarkusAugmentationResult(
        Path augmentationDirectory,
        Path metadataPath,
        String inputFingerprint) {
    public QuarkusAugmentationResult {
        if (augmentationDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires an augmentation directory.");
        }
        if (metadataPath == null) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires a metadata path.");
        }
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus augmentation result requires an input fingerprint.");
        }
    }
}
