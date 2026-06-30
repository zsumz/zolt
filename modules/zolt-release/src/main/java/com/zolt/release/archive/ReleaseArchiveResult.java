package com.zolt.release.archive;

import com.zolt.release.ReleaseTarget;
import java.nio.file.Path;

public record ReleaseArchiveResult(
        ReleaseTarget target,
        Path archivePath,
        Path checksumPath,
        Path manifestPath,
        String rootDirectory,
        String sha256,
        int fileCount) {
}
