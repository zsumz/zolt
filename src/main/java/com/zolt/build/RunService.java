package com.zolt.build;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusAugmentationResult;
import com.zolt.quarkus.QuarkusBuildAugmentationService;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

public final class RunService {
    private final BuildService buildService;
    private final ZoltLockfileReader lockfileReader;
    private final ClasspathBuilder classpathBuilder;
    private final JdkDetector jdkDetector;
    private final JavaRunner javaRunner;
    private final QuarkusBuildAugmenter quarkusBuildAugmenter;

    public RunService() {
        this(
                new BuildService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(),
                (projectDirectory, config, cacheRoot) ->
                        new QuarkusBuildAugmentationService().augmentIfEnabled(projectDirectory, config, cacheRoot));
    }

    RunService(
            BuildService buildService,
            ZoltLockfileReader lockfileReader,
            ClasspathBuilder classpathBuilder,
            JdkDetector jdkDetector,
            JavaRunner javaRunner,
            QuarkusBuildAugmenter quarkusBuildAugmenter) {
        this.buildService = buildService;
        this.lockfileReader = lockfileReader;
        this.classpathBuilder = classpathBuilder;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.quarkusBuildAugmenter = quarkusBuildAugmenter;
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
        BuildResult buildResult = buildService.build(projectDirectory, config, cacheRoot);
        Optional<QuarkusAugmentationResult> quarkusResult =
                quarkusBuildAugmenter.augmentIfEnabled(projectDirectory, config, cacheRoot);

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new RunException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        if (quarkusResult.isPresent()) {
            Path runnerJar = quarkusResult.orElseThrow().workerResult().runnerJar();
            JavaRunResult javaRunResult = javaRunner.runJar(
                    jdkStatus.java().orElseThrow(),
                    runnerJar,
                    "Quarkus runner " + runnerJar,
                    arguments,
                    outputConsumer);
            return new RunResult(buildResult, javaRunResult);
        }

        ZoltLockfile lockfile = lockfileReader.read(projectDirectory.resolve("zolt.lock"));
        ClasspathSet classpaths = classpathBuilder.build(lockfileReader.classpathPackages(lockfile, cacheRoot));
        List<Path> runtimeEntries = new ArrayList<>();
        runtimeEntries.add(buildResult.outputDirectory());
        runtimeEntries.addAll(classpaths.runtime().entries());
        JavaRunResult javaRunResult = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(runtimeEntries),
                mainClass,
                arguments,
                outputConsumer);
        return new RunResult(buildResult, javaRunResult);
    }

    @FunctionalInterface
    interface QuarkusBuildAugmenter {
        Optional<QuarkusAugmentationResult> augmentIfEnabled(
                Path projectDirectory,
                ProjectConfig config,
                Path cacheRoot);
    }
}
