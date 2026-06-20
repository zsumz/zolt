package com.zolt.framework;

import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface FrameworkRunAugmenter {
    Optional<FrameworkRunResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot);

    default boolean isEnabled(ProjectConfig config) {
        return false;
    }

    static FrameworkRunAugmenter none() {
        return (projectDirectory, config, cacheRoot) -> Optional.empty();
    }
}
