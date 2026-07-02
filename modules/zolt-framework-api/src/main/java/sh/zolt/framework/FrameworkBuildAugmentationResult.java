package sh.zolt.framework;

import java.nio.file.Path;

public record FrameworkBuildAugmentationResult(
        String displayName,
        Path runnerJar) {
    public FrameworkBuildAugmentationResult {
        if (displayName == null || displayName.isBlank()) {
            throw new IllegalArgumentException("Framework build augmentation result requires a display name.");
        }
        if (runnerJar == null) {
            throw new IllegalArgumentException("Framework build augmentation result requires a runner jar.");
        }
    }
}
