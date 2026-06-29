package com.zolt.quarkus;

import com.zolt.framework.FrameworkBuildAugmentationResult;
import com.zolt.framework.FrameworkBuildAugmenter;
import com.zolt.framework.FrameworkBuildException;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.production.QuarkusBuildAugmentationService;
import java.nio.file.Path;
import java.util.Optional;

public final class QuarkusBuildAugmenter implements FrameworkBuildAugmenter {
    private final QuarkusBuildAugmentationService augmentationService;

    public QuarkusBuildAugmenter() {
        this(new QuarkusBuildAugmentationService());
    }

    QuarkusBuildAugmenter(QuarkusBuildAugmentationService augmentationService) {
        this.augmentationService = augmentationService;
    }

    @Override
    public Optional<FrameworkBuildAugmentationResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot) {
        try {
            return augmentationService.augmentIfEnabled(projectDirectory, config, cacheRoot)
                    .map(result -> new FrameworkBuildAugmentationResult(
                            "Quarkus",
                            result.workerResult().runnerJar()));
        } catch (QuarkusAugmentationException | QuarkusPlanException exception) {
            throw new FrameworkBuildException(exception.getMessage(), exception);
        }
    }
}
