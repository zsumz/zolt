package com.zolt.quarkus.bootstrap;

import com.zolt.dependency.PackageId;
import com.zolt.quarkus.QuarkusAugmentationException;
import java.nio.file.Path;

public record QuarkusApplicationArtifact(
        PackageId packageId,
        String version,
        Path path) {
    public QuarkusApplicationArtifact {
        if (packageId == null) {
            throw new QuarkusAugmentationException("Quarkus application artifact requires a package id.");
        }
        if (version == null || version.isBlank()) {
            throw new QuarkusAugmentationException("Quarkus application artifact requires a version.");
        }
        if (path == null) {
            throw new QuarkusAugmentationException("Quarkus application artifact requires an output path.");
        }
    }

    public String type() {
        return "jar";
    }

    public String classifier() {
        return "";
    }
}
