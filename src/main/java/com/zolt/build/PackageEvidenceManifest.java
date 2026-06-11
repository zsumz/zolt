package com.zolt.build;

import java.util.List;

public record PackageEvidenceManifest(
        String schema,
        String archive,
        String archiveSha256,
        List<PackageEvidenceArtifact> artifacts) {
    public PackageEvidenceManifest {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public PackageEvidenceManifest(
            String schema,
            String archive,
            String archiveSha256) {
        this(schema, archive, archiveSha256, List.of());
    }
}
