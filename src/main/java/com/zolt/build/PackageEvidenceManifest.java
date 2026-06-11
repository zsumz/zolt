package com.zolt.build;

public record PackageEvidenceManifest(
        String schema,
        String archive,
        String archiveSha256) {
}
