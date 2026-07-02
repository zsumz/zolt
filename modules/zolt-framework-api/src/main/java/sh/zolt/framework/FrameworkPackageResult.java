package sh.zolt.framework;

import sh.zolt.project.PackageMode;
import java.nio.file.Path;

public record FrameworkPackageResult(
        PackageMode mode,
        Path packageDirectory,
        Path runnerJar,
        String applicationLayout) {
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
        if (applicationLayout == null || applicationLayout.isBlank()) {
            throw new IllegalArgumentException("Framework package result requires an application layout.");
        }
    }

    public FrameworkPackageResult(
            PackageMode mode,
            Path packageDirectory,
            Path runnerJar) {
        this(mode, packageDirectory, runnerJar, "framework package output");
    }
}
