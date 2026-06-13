package com.zolt.framework;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface FrameworkPackageAugmenter {
    Optional<FrameworkPackageResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot);

    static FrameworkPackageAugmenter none() {
        return (projectDirectory, config, cacheRoot) -> Optional.empty();
    }
}
