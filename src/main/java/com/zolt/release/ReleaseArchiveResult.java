package com.zolt.release;

import java.nio.file.Path;

public record ReleaseArchiveResult(
        ReleaseTarget target,
        Path archivePath,
        String rootDirectory,
        int fileCount) {
}
