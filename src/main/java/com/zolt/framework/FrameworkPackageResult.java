package com.zolt.framework;

import com.zolt.project.PackageMode;
import java.nio.file.Path;

public record FrameworkPackageResult(
        PackageMode mode,
        Path packageDirectory,
        Path runnerJar) {
    public FrameworkPackageResult {
        if (mode == null) {
            throw new IllegalArgumentException("Framework package result requires a package mode.");
        }
        if (packageDirectory == null) {
            throw new IllegalArgumentException("Framework package result requires a package directory.");
        }
        if (runnerJar == null) {
            throw new IllegalArgumentException("Framework package result requires a runner jar.");
        }
    }
}
