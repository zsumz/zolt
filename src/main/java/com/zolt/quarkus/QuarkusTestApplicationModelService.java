package com.zolt.quarkus;

import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public final class QuarkusTestApplicationModelService {
    public static final String SERIALIZED_TEST_MODEL_PROPERTY =
            "quarkus-internal-test.serialized-app-model.path";

    private final QuarkusBootstrapDescriptorReader descriptorReader;
    private final JdkDetector jdkDetector;
    private final Supplier<List<Path>> workerClasspath;
    private final LauncherFactory launcherFactory;

    public QuarkusTestApplicationModelService() {
        this(
                new QuarkusBootstrapDescriptorReader(),
                new JdkDetector(),
                QuarkusTestApplicationModelService::currentWorkerClasspath,
                QuarkusTestApplicationModelWorkerLauncher::new);
    }

    QuarkusTestApplicationModelService(
            QuarkusBootstrapDescriptorReader descriptorReader,
            JdkDetector jdkDetector,
            Supplier<List<Path>> workerClasspath,
            LauncherFactory launcherFactory) {
        if (descriptorReader == null) {
            throw new QuarkusAugmentationException("Quarkus bootstrap descriptor reader is required.");
        }
        if (jdkDetector == null) {
            throw new QuarkusAugmentationException("JDK detector is required for Quarkus test application models.");
        }
        if (workerClasspath == null) {
            throw new QuarkusAugmentationException("Quarkus test application model worker classpath supplier is required.");
        }
        if (launcherFactory == null) {
            throw new QuarkusAugmentationException("Quarkus test application model launcher factory is required.");
        }
        this.descriptorReader = descriptorReader;
        this.jdkDetector = jdkDetector;
        this.workerClasspath = workerClasspath;
        this.launcherFactory = launcherFactory;
    }

    public Optional<Path> writeIfEnabled(Path projectDirectory, ProjectConfig config) {
        if (projectDirectory == null) {
            throw new QuarkusAugmentationException("Quarkus test application model requires a project directory.");
        }
        if (config == null) {
            throw new QuarkusAugmentationException("Quarkus test application model requires a project config.");
        }
        if (!config.frameworkSettings().quarkus().enabled()) {
            return Optional.empty();
        }

        JdkStatus status = jdkDetector.detect(config.project().java());
        if (!status.ok()) {
            throw new QuarkusAugmentationException(
                    "JDK check failed for Quarkus test application model. " + String.join(" ", status.problems()));
        }
        QuarkusBootstrapDescriptor descriptor = descriptorReader.read(
                projectDirectory.resolve("target/quarkus/zolt-bootstrap.properties"));
        Path outputPath = projectDirectory.resolve("target/quarkus/test-application-model.dat");
        return Optional.of(launcherFactory.create(status.java().orElseThrow(), workerClasspath.get())
                .write(descriptor, outputPath, workspaceModuleInputs(projectDirectory, config)));
    }

    private static QuarkusWorkspaceModuleInputs workspaceModuleInputs(Path projectDirectory, ProjectConfig config) {
        return new QuarkusWorkspaceModuleInputs(
                projectDirectory,
                projectDirectory.resolve("target"),
                projectDirectory.resolve(config.build().source()),
                projectDirectory.resolve(first(config.build().resourceRoots(), "src/main/resources")),
                projectDirectory.resolve(config.build().output()),
                projectDirectory.resolve(first(config.build().testSources(), config.build().test())),
                projectDirectory.resolve(first(config.build().testResourceRoots(), "src/test/resources")),
                projectDirectory.resolve(config.build().testOutput()));
    }

    private static String first(List<String> values, String fallback) {
        if (values == null || values.isEmpty()) {
            return fallback;
        }
        return values.getFirst();
    }

    private static List<Path> currentWorkerClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        List<Path> entries = Arrays.stream(classpath.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();
        if (entries.isEmpty()) {
            throw new QuarkusAugmentationException(
                    "Could not determine Zolt worker classpath for Quarkus test application model. "
                            + "Run zolt test from the packaged launcher or check java.class.path.");
        }
        return entries;
    }

    @FunctionalInterface
    interface LauncherFactory {
        QuarkusTestApplicationModelWorkerLauncher create(Path javaExecutable, List<Path> workerClasspath);
    }
}
