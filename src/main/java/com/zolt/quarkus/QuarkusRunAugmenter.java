package com.zolt.quarkus;

import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkRunException;
import com.zolt.framework.FrameworkRunResult;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Optional;

public final class QuarkusRunAugmenter implements FrameworkRunAugmenter {
    private final QuarkusBuildAugmentationService augmentationService;

    public QuarkusRunAugmenter() {
        this(new QuarkusBuildAugmentationService());
    }

    QuarkusRunAugmenter(QuarkusBuildAugmentationService augmentationService) {
        this.augmentationService = augmentationService;
    }

    @Override
    public boolean isEnabled(ProjectConfig config) {
        return config.frameworkSettings().quarkus().enabled();
    }

    @Override
    public Optional<FrameworkRunResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot) {
        try {
            return augmentationService.augmentIfEnabled(projectDirectory, config, cacheRoot)
                    .map(result -> new FrameworkRunResult(
                            result.workerResult().runnerJar(),
                            "Quarkus runner " + result.workerResult().runnerJar()));
        } catch (QuarkusAugmentationException | QuarkusPlanException exception) {
            throw new FrameworkRunException(exception.getMessage(), exception);
        }
    }
}
