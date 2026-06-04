package com.zolt.build;

import com.zolt.project.PackageMode;
import java.nio.file.Path;
import java.util.Optional;

public record PackageResult(
        BuildResult buildResult,
        PackageMode mode,
        Path jarPath,
        Optional<Path> runtimeClasspathPath,
        int entryCount,
        boolean hasMainClass) {
    public PackageResult {
        mode = mode == null ? PackageMode.THIN : mode;
        runtimeClasspathPath = runtimeClasspathPath == null ? Optional.empty() : runtimeClasspathPath;
    }

    public PackageResult(
            BuildResult buildResult,
            Path jarPath,
            Optional<Path> runtimeClasspathPath,
            int entryCount,
            boolean hasMainClass) {
        this(buildResult, PackageMode.THIN, jarPath, runtimeClasspathPath, entryCount, hasMainClass);
    }

    public PackageResult(
            BuildResult buildResult,
            Path jarPath,
            int entryCount,
            boolean hasMainClass) {
        this(buildResult, PackageMode.THIN, jarPath, Optional.empty(), entryCount, hasMainClass);
    }
}
