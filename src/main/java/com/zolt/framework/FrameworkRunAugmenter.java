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

    static FrameworkRunAugmenter none() {
        return (projectDirectory, config, cacheRoot) -> Optional.empty();
    }
}
