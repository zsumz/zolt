package com.zolt.quarkus;

import com.zolt.resolve.PackageId;
import java.nio.file.Path;

public record QuarkusPlatformPropertiesArtifact(
        PackageId packageId,
        String version,
        Path path) {
    public QuarkusPlatformPropertiesArtifact {
        if (packageId == null) {
            throw new QuarkusPlanException("Quarkus platform properties artifact requires a package id.");
        }
        if (version == null || version.isBlank()) {
            throw new QuarkusPlanException("Quarkus platform properties artifact requires a version.");
        }
        if (path == null) {
            throw new QuarkusPlanException("Quarkus platform properties artifact requires a path.");
        }
    }
}
