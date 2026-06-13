package com.zolt.build;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkRunAugmenter;
import com.zolt.framework.FrameworkRunResult;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class RunService {
    private final BuildService buildService;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;
    private final FrameworkRunAugmenter frameworkRunAugmenter;

    public RunService() {
        this(new JdkDetector());
    }

    public RunService(JdkChecker jdkDetector) {
        this(jdkDetector, FrameworkRunAugmenter.none());
    }

    public RunService(FrameworkRunAugmenter frameworkRunAugmenter) {
        this(new JdkDetector(), frameworkRunAugmenter);
    }

    public RunService(JdkChecker jdkDetector, FrameworkRunAugmenter frameworkRunAugmenter) {
        this(
                new BuildService(jdkDetector),
                jdkDetector,
                new JavaRunner(),
                frameworkRunAugmenter);
    }

    RunService(
            BuildService buildService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            FrameworkRunAugmenter frameworkRunAugmenter) {
        this.buildService = buildService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.frameworkRunAugmenter = frameworkRunAugmenter;
    }

    public RunResult run(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            List<String> arguments) {
        return run(projectDirectory, config, cacheRoot, arguments, ignored -> {
        });
    }

    public RunResult run(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            List<String> arguments,
            Consumer<String> outputConsumer) {
        boolean quarkusEnabled = config.frameworkSettings().quarkus().enabled();
        String mainClass = quarkusEnabled
                ? null
                : config.project().main().orElseThrow(() -> new RunException(
                        "No main class is configured. Add [project].main to zolt.toml."));
        BuildResultWithClasspaths buildResult = buildService.buildWithClasspaths(
                projectDirectory,
                config,
                cacheRoot,
                false);
        Optional<FrameworkRunResult> frameworkRunResult =
                frameworkRunAugmenter.augmentIfEnabled(projectDirectory, config, cacheRoot);

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new RunException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        if (frameworkRunResult.isPresent()) {
            FrameworkRunResult runResult = frameworkRunResult.orElseThrow();
            JavaRunResult javaRunResult = javaRunner.runJar(
                    jdkStatus.java().orElseThrow(),
                    runResult.runnerJar(),
                    runResult.runnerDescription(),
                    arguments,
                    outputConsumer);
            return new RunResult(buildResult.buildResult(), javaRunResult);
        }

        List<Path> runtimeEntries = new ArrayList<>();
        runtimeEntries.add(buildResult.buildResult().outputDirectory());
        runtimeEntries.addAll(buildResult.classpaths().runtime().entries());
        JavaRunResult javaRunResult = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(runtimeEntries),
                mainClass,
                arguments,
                outputConsumer);
        return new RunResult(buildResult.buildResult(), javaRunResult);
    }
}
