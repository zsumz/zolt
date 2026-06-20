package com.zolt.quarkus;

import java.nio.file.Path;

public record QuarkusBootstrapWorkerResult(
        String inputFingerprint,
        Path packageDirectory,
        Path runnerJar,
        Path libraryDirectory,
        int artifactResultCount) {
    public QuarkusBootstrapWorkerResult {
        if (inputFingerprint == null || inputFingerprint.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result requires an input fingerprint.");
        }
        if (packageDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result requires a package directory.");
        }
        if (runnerJar == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result requires a runner jar.");
        }
        if (artifactResultCount < 0) {
            throw new QuarkusAugmentationException("Quarkus bootstrap worker result artifact count cannot be negative.");
        }
    }
}
