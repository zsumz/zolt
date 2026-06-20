package com.zolt.build;

public record PackageEvidenceArtifact(
        String classifier,
        String type,
        String path,
        int entries,
        String sha256) {
    public PackageEvidenceArtifact {
        if (classifier == null || classifier.isBlank()) {
            throw new PackageException("Package evidence artifact classifier is required.");
        }
        if (type == null || type.isBlank()) {
            throw new PackageException("Package evidence artifact type is required.");
        }
        if (path == null || path.isBlank()) {
            throw new PackageException("Package evidence artifact path is required.");
        }
        if (sha256 == null || sha256.isBlank()) {
            throw new PackageException("Package evidence artifact checksum is required.");
        }
    }
}
