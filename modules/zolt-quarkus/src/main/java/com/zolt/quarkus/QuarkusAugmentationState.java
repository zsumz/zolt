package com.zolt.quarkus;

import java.nio.file.Path;
import java.util.Optional;

public record QuarkusAugmentationState(
        Path metadataPath,
        Status status,
        Optional<String> recordedInputFingerprint) {
    public QuarkusAugmentationState {
        if (metadataPath == null) {
            throw new QuarkusPlanException("Quarkus augmentation metadata path is required.");
        }
        if (status == null) {
            throw new QuarkusPlanException("Quarkus augmentation metadata status is required.");
        }
        recordedInputFingerprint = recordedInputFingerprint == null ? Optional.empty() : recordedInputFingerprint;
    }

    public enum Status {
        MISSING("missing"),
        CURRENT("current"),
        STALE("stale");

        private final String label;

        Status(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }
}
