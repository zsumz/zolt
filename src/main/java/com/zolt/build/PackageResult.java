package com.zolt.build;

import java.nio.file.Path;
import java.util.Optional;

public record PackageResult(
        BuildResult buildResult,
        Path jarPath,
        Optional<Path> runtimeClasspathPath,
        int entryCount,
        boolean hasMainClass) {
    public PackageResult {
        runtimeClasspathPath = runtimeClasspathPath == null ? Optional.empty() : runtimeClasspathPath;
    }

    public PackageResult(
            BuildResult buildResult,
            Path jarPath,
            int entryCount,
            boolean hasMainClass) {
        this(buildResult, jarPath, Optional.empty(), entryCount, hasMainClass);
    }
}
