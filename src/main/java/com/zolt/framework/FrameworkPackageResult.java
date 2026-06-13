package com.zolt.framework;

import java.nio.file.Path;

public record FrameworkPackageResult(
        Path packageDirectory,
        Path runnerJar) {
    public FrameworkPackageResult {
        if (packageDirectory == null) {
            throw new IllegalArgumentException("Framework package result requires a package directory.");
        }
        if (runnerJar == null) {
            throw new IllegalArgumentException("Framework package result requires a runner jar.");
        }
    }
}
