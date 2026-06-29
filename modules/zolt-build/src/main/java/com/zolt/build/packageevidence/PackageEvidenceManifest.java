package com.zolt.build.packageevidence;

import com.zolt.build.PackageMergeDecision;
import java.util.List;

public record PackageEvidenceManifest(
        String schema,
        String archive,
        String archiveSha256,
        List<PackageEvidenceArtifact> artifacts,
        List<PackageMergeDecision> uberMergeDecisions) {
    public PackageEvidenceManifest {
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        uberMergeDecisions = uberMergeDecisions == null ? List.of() : List.copyOf(uberMergeDecisions);
    }

    public PackageEvidenceManifest(
            String schema,
            String archive,
            String archiveSha256,
            List<PackageEvidenceArtifact> artifacts) {
        this(schema, archive, archiveSha256, artifacts, List.of());
    }

    public PackageEvidenceManifest(
            String schema,
            String archive,
            String archiveSha256) {
        this(schema, archive, archiveSha256, List.of());
    }
}
