package com.zolt.build;

import com.zolt.project.PackageMode;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public record PackageResult(
        BuildResult buildResult,
        PackageMode mode,
        Path jarPath,
        Optional<Path> runtimeClasspathPath,
        Optional<Path> evidenceManifestPath,
        int entryCount,
        boolean hasMainClass,
        List<PackageArtifact> artifacts) {
    public PackageResult {
        mode = mode == null ? PackageMode.THIN : mode;
        runtimeClasspathPath = runtimeClasspathPath == null ? Optional.empty() : runtimeClasspathPath;
        evidenceManifestPath = evidenceManifestPath == null ? Optional.empty() : evidenceManifestPath;
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
    }

    public PackageResult withArtifactsAndEvidence(
            List<PackageArtifact> artifacts,
            Optional<Path> evidenceManifestPath) {
        return new PackageResult(
                buildResult,
                mode,
                jarPath,
                runtimeClasspathPath,
                evidenceManifestPath,
                entryCount,
                hasMainClass,
                artifacts);
    }

    public PackageResult(
            BuildResult buildResult,
            PackageMode mode,
            Path jarPath,
            Optional<Path> runtimeClasspathPath,
            int entryCount,
            boolean hasMainClass) {
        this(buildResult, mode, jarPath, runtimeClasspathPath, Optional.empty(), entryCount, hasMainClass, List.of());
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
