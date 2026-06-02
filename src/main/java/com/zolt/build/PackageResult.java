package com.zolt.build;

import java.nio.file.Path;

public record PackageResult(
        BuildResult buildResult,
        Path jarPath,
        int entryCount,
        boolean hasMainClass) {
}
