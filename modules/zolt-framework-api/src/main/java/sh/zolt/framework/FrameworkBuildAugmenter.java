package sh.zolt.framework;

import sh.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

@FunctionalInterface
public interface FrameworkBuildAugmenter {
    Optional<FrameworkBuildAugmentationResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot);

    static FrameworkBuildAugmenter none() {
        return (projectDirectory, config, cacheRoot) -> Optional.empty();
    }
}
