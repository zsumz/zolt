package com.zolt.build.packaging;

import com.zolt.build.PackageException;
import java.nio.file.Path;

public record PackageArtifact(
        String classifier,
        Path path,
        int entryCount) {
    public PackageArtifact {
        if (classifier == null || classifier.isBlank()) {
            throw new PackageException("Package artifact classifier is required.");
        }
        if (path == null) {
            throw new PackageException("Package artifact path is required.");
        }
    }
}
