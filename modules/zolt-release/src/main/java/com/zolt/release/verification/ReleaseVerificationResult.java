package com.zolt.release.verification;

import java.nio.file.Path;
import java.util.List;

public record ReleaseVerificationResult(
        List<VerifiedArchive> archives) {
    public ReleaseVerificationResult {
        archives = List.copyOf(archives);
    }

    public int verifiedCount() {
        return archives.size();
    }

    public record VerifiedArchive(
            Path archivePath,
            Path unpackDirectory,
            Path binaryPath) {
    }
}
