package com.zolt.quarkus;

import com.zolt.framework.FrameworkPackageAugmenter;
import com.zolt.framework.FrameworkPackageResult;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

public final class QuarkusPackageAugmenter implements FrameworkPackageAugmenter {
    private final QuarkusBuildAugmentationService augmentationService;

    public QuarkusPackageAugmenter() {
        this(new QuarkusBuildAugmentationService());
    }

    QuarkusPackageAugmenter(QuarkusBuildAugmentationService augmentationService) {
        this.augmentationService = augmentationService;
    }

    @Override
    public Optional<FrameworkPackageResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot) {
        return augmentationService.augmentIfEnabled(projectDirectory, config, cacheRoot)
                .map(result -> new FrameworkPackageResult(
                        result.workerResult().packageDirectory(),
                        result.workerResult().runnerJar()));
    }
}
