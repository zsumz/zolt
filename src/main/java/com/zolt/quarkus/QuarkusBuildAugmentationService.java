package com.zolt.quarkus;

import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class QuarkusBuildAugmentationService {
    private final QuarkusPlanner planner;
    private final QuarkusRequestCreator requestCreator;
    private final QuarkusAugmentationRunner runner;

    public QuarkusBuildAugmentationService() {
        this(
                (projectDirectory, config, cacheRoot) ->
                        new QuarkusPlanService().plan(projectDirectory, config, cacheRoot),
                plan -> new QuarkusAugmentationRequestFactory().create(plan),
                new WorkerBackedAugmentationRunner(
                        new JdkDetector(),
                        QuarkusBuildAugmentationService::currentWorkerClasspath));
    }

    QuarkusBuildAugmentationService(
            QuarkusPlanner planner,
            QuarkusRequestCreator requestCreator,
            QuarkusAugmentationRunner runner) {
        if (planner == null) {
            throw new QuarkusAugmentationException("Quarkus build planner is required.");
        }
        if (requestCreator == null) {
            throw new QuarkusAugmentationException("Quarkus build request creator is required.");
        }
        if (runner == null) {
            throw new QuarkusAugmentationException("Quarkus build augmentation runner is required.");
        }
        this.planner = planner;
        this.requestCreator = requestCreator;
        this.runner = runner;
    }

    public Optional<QuarkusAugmentationResult> augmentIfEnabled(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot) {
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus build augmentation requires a project directory.");
        }
        if (config == null) {
            throw new QuarkusAugmentationException("Quarkus build augmentation requires a project config.");
        }
        if (cacheRoot == null) {
            throw new QuarkusAugmentationException("Quarkus build augmentation requires a cache root.");
        }
        if (!config.frameworkSettings().quarkus().enabled()) {
            return Optional.empty();
        }
        QuarkusPlan plan = planner.plan(projectDirectory, config, cacheRoot);
        QuarkusAugmentationRequest request = requestCreator.create(plan);
        return Optional.of(runner.augment(config, request));
    }

    private static List<Path> currentWorkerClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        List<Path> entries = Arrays.stream(classpath.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();
        if (entries.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Could not determine Zolt worker classpath for Quarkus augmentation. "
                            + "Run zolt build from the packaged launcher or check java.class.path.");
        }
        return entries;
    }

    @FunctionalInterface
    interface QuarkusPlanner {
        QuarkusPlan plan(Path projectDirectory, ProjectConfig config, Path cacheRoot);
    }

    @FunctionalInterface
    interface QuarkusRequestCreator {
        QuarkusAugmentationRequest create(QuarkusPlan plan);
    }

    @FunctionalInterface
    interface QuarkusAugmentationRunner {
        QuarkusAugmentationResult augment(ProjectConfig config, QuarkusAugmentationRequest request);
    }

    private static final class WorkerBackedAugmentationRunner implements QuarkusAugmentationRunner {
        private final JdkDetector jdkDetector;
        private final Supplier<List<Path>> workerClasspath;

        private WorkerBackedAugmentationRunner(
                JdkDetector jdkDetector,
                Supplier<List<Path>> workerClasspath) {
            this.jdkDetector = jdkDetector;
            this.workerClasspath = workerClasspath;
        }

        @Override
        public QuarkusAugmentationResult augment(ProjectConfig config, QuarkusAugmentationRequest request) {
            JdkStatus status = jdkDetector.detect(config.project().java());
            if (!status.ok()) {
                throw new QuarkusAugmentationException(
                        "JDK check failed for Quarkus augmentation. " + String.join(" ", status.problems()));
            }
            QuarkusAugmentor augmentor = new QuarkusBootstrapWorkerLauncher(
                    status.java().orElseThrow(),
                    workerClasspath.get());
            return new QuarkusAugmentationExecutor(augmentor).augment(request);
        }
    }
}
