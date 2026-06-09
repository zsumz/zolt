package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusPlanException;
import com.zolt.quarkus.QuarkusTestApplicationModelService;
import com.zolt.quarkus.QuarkusTestPlan;
import com.zolt.quarkus.QuarkusTestPlanService;
import com.zolt.quarkus.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.QuarkusTestRunnerDescriptorWriter;
import com.zolt.quarkus.QuarkusTestRunnerRequest;
import com.zolt.quarkus.QuarkusTestWorkerLauncher;
import com.zolt.quarkus.QuarkusUnsupportedTest;
import com.zolt.resolve.Classpath;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;

public final class TestRunService {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JBOSS_LOG_MANAGER_PROPERTY =
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager";

    private final TestCompileService testCompileService;
    private final JdkDetector jdkDetector;
    private final JavaRunner javaRunner;
    private final QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter;
    private final QuarkusTestRunnerDescriptorWriter quarkusTestRunnerDescriptorWriter;
    private final Supplier<List<Path>> quarkusTestWorkerClasspath;
    private final QuarkusTestWorkerRunner quarkusTestWorkerRunner;
    private final String pathSeparator;

    public TestRunService() {
        this(
                new TestCompileService(),
                new JdkDetector(),
                new JavaRunner(),
                new QuarkusTestApplicationModelService()::writeIfEnabled,
                new QuarkusTestRunnerDescriptorWriter(),
                TestRunService::currentWorkerClasspath,
                (javaExecutable, workerClasspath, descriptor) ->
                        new QuarkusTestWorkerLauncher(javaExecutable, workerClasspath).run(descriptor),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            JdkDetector jdkDetector,
            JavaRunner javaRunner,
            QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            QuarkusTestRunnerDescriptorWriter quarkusTestRunnerDescriptorWriter,
            Supplier<List<Path>> quarkusTestWorkerClasspath,
            QuarkusTestWorkerRunner quarkusTestWorkerRunner,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.quarkusTestApplicationModelWriter = quarkusTestApplicationModelWriter;
        this.quarkusTestRunnerDescriptorWriter = quarkusTestRunnerDescriptorWriter;
        this.quarkusTestWorkerClasspath = quarkusTestWorkerClasspath;
        this.quarkusTestWorkerRunner = quarkusTestWorkerRunner;
        this.pathSeparator = pathSeparator;
    }

    public TestRunResult runTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        TestCompileResultWithClasspaths compileResult =
                compileTests(projectDirectory, config, cacheRoot);
        return runCompiledTests(projectDirectory, config, compileResult.classpaths(), compileResult.testCompileResult());
    }

    public TestCompileResultWithClasspaths compileTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return testCompileService.compileTestsWithClasspaths(projectDirectory, config, cacheRoot);
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult) {
        return runTests(projectDirectory, config, classpaths, compileResult);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        TestCompileResult compileResult = compileTests(projectDirectory, config, classpaths, buildResult);
        return runTests(projectDirectory, config, classpaths, compileResult);
    }

    public TestCompileResult compileTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        return testCompileService.compileTests(
                projectDirectory,
                config,
                classpaths,
                buildResult);
    }

    private TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult) {
        List<Path> runnerClasspath = new ArrayList<>();
        runnerClasspath.add(compileResult.outputDirectory());
        runnerClasspath.add(compileResult.buildResult().outputDirectory());
        runnerClasspath.addAll(classpaths.test().entries());
        runnerClasspath = absolutePaths(runnerClasspath);
        if (runnerClasspath.stream().noneMatch(TestRunService::isConsoleJar)) {
            throw new TestRunException(
                    "JUnit Platform Console is not present on the test classpath. "
                            + "Run `zolt resolve` to refresh Zolt's test runner tooling. "
                            + "Keep JUnit, Spock, and other test engines declared in [test.dependencies].");
        }

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        Optional<Path> serializedApplicationModel = writeQuarkusTestApplicationModel(projectDirectory, config);
        Optional<QuarkusTestRunnerDescriptor> quarkusTestRunnerDescriptor = writeQuarkusTestRunnerDescriptor(
                projectDirectory,
                config,
                compileResult,
                runnerClasspath,
                serializedApplicationModel);
        List<Path> launcherClasspath = junitLauncherClasspath(runnerClasspath);
        if (config.frameworkSettings().quarkus().enabled()) {
            QuarkusTestRunnerDescriptor descriptor = quarkusTestRunnerDescriptor.orElseThrow();
            failOnUnsupportedQuarkusTests(
                    projectDirectory,
                    config,
                    descriptor.supportsQuarkusTestAnnotations());
            List<Path> workerClasspath = quarkusTestWorkerClasspath.get();
            String output = runQuarkusPlainJunitWorker(
                    jdkStatus.java().orElseThrow(),
                    workerClasspath,
                    descriptor);
            failOnHiddenQuarkusBootstrapFailure(config, output);
            return new TestRunResult(compileResult, output, runnerClasspath.size(), workerClasspath.size());
        }
        JavaRunResult result = javaRunner.run(
                jdkStatus.java().orElseThrow(),
                new Classpath(launcherClasspath),
                CONSOLE_MAIN_CLASS,
                jvmArguments(projectDirectory, runnerClasspath, serializedApplicationModel),
                List.of(
                        "execute",
                        "--disable-banner",
                        "--class-path", joined(runnerClasspath),
                        "--scan-class-path",
                        "--details", "summary"));
        failOnHiddenQuarkusBootstrapFailure(config, result.output());
        return new TestRunResult(compileResult, result.output(), runnerClasspath.size(), launcherClasspath.size());
    }

    private String joined(List<Path> classpath) {
        StringJoiner joiner = new StringJoiner(pathSeparator);
        for (Path entry : classpath) {
            joiner.add(entry.normalize().toString());
        }
        return joiner.toString();
    }

    private static List<Path> absolutePaths(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private String runQuarkusPlainJunitWorker(
            Path javaExecutable,
            List<Path> workerClasspath,
            QuarkusTestRunnerDescriptor descriptor) {
        try {
            return quarkusTestWorkerRunner.run(javaExecutable, workerClasspath, descriptor);
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    static void failOnUnsupportedQuarkusTests(Path projectDirectory, ProjectConfig config) {
        failOnUnsupportedQuarkusTests(projectDirectory, config, false);
    }

    static void failOnUnsupportedQuarkusTests(
            Path projectDirectory,
            ProjectConfig config,
            boolean supportsQuarkusTestAnnotations) {
        if (config == null || !config.frameworkSettings().quarkus().enabled()) {
            return;
        }
        if (supportsQuarkusTestAnnotations) {
            return;
        }
        try {
            QuarkusTestPlan plan = new QuarkusTestPlanService().plan(projectDirectory, config);
            if (plan.hasUnsupportedTests()) {
                QuarkusUnsupportedTest firstUnsupportedTest = plan.unsupportedTests().getFirst();
                throw new TestRunException(
                        "Quarkus-specific `" + firstUnsupportedTest.annotationName()
                                + "` execution is not supported by Zolt's current test runner. "
                                + "Use plain JUnit tests for now, or remove `" + firstUnsupportedTest.annotationName()
                                + "` until Zolt's dedicated "
                                + "Quarkus test runner is implemented. Found "
                                + firstUnsupportedTest.relativePath()
                                + ".");
            }
        } catch (QuarkusPlanException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    private static boolean isConsoleJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console") && name.endsWith(".jar");
    }

    private static List<Path> junitLauncherClasspath(List<Path> runnerClasspath) {
        List<Path> launcherClasspath = new ArrayList<>();
        boolean hasStandaloneConsole = runnerClasspath.stream().anyMatch(TestRunService::isStandaloneConsoleJar);
        for (Path entry : runnerClasspath) {
            if (hasStandaloneConsole) {
                if (isStandaloneConsoleJar(entry) || isJbossLogManagerJar(entry)) {
                    launcherClasspath.add(entry);
                }
            } else if (isJunitPlatformRuntimeJar(entry)
                    || isJunitPlatformSupportJar(entry)
                    || isJbossLogManagerJar(entry)) {
                launcherClasspath.add(entry);
            }
        }
        return List.copyOf(launcherClasspath);
    }

    private static boolean isStandaloneConsoleJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console-standalone-") && name.endsWith(".jar");
    }

    private static boolean isJunitPlatformRuntimeJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("junit-platform-console-")
                || name.startsWith("junit-platform-reporting-")
                || name.startsWith("junit-platform-launcher-")
                || name.startsWith("junit-platform-engine-")
                || name.startsWith("junit-platform-commons-");
    }

    private static boolean isJunitPlatformSupportJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("apiguardian-api-") || name.startsWith("opentest4j-");
    }

    private List<String> jvmArguments(
            Path projectDirectory,
            List<Path> runnerClasspath,
            Optional<Path> serializedApplicationModel) {
        List<String> arguments = new ArrayList<>();
        arguments.add("-Duser.dir=" + projectDirectory.toAbsolutePath().normalize());
        serializedApplicationModel
                .ifPresent(path -> arguments.add("-D"
                        + QuarkusTestApplicationModelService.SERIALIZED_TEST_MODEL_PROPERTY
                        + "="
                        + path));
        if (runnerClasspath.stream().anyMatch(TestRunService::isJbossLogManagerJar)) {
            arguments.add(JBOSS_LOG_MANAGER_PROPERTY);
        }
        return List.copyOf(arguments);
    }

    private Optional<Path> writeQuarkusTestApplicationModel(Path projectDirectory, ProjectConfig config) {
        try {
            return quarkusTestApplicationModelWriter.write(projectDirectory, config);
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(
                    "Could not prepare Quarkus test application model. "
                            + "Run `zolt resolve`, then run `zolt test` again. "
                            + exception.getMessage(),
                    exception);
        }
    }

    private Optional<QuarkusTestRunnerDescriptor> writeQuarkusTestRunnerDescriptor(
            Path projectDirectory,
            ProjectConfig config,
            TestCompileResult compileResult,
            List<Path> runnerClasspath,
            Optional<Path> serializedApplicationModel) {
        if (!config.frameworkSettings().quarkus().enabled()) {
            return Optional.empty();
        }
        Path modelPath = serializedApplicationModel.orElseThrow(() -> new TestRunException(
                "Could not prepare Quarkus test runner descriptor because the serialized application model was not written. "
                        + "Run `zolt build`, then run `zolt test` again."));
        try {
            return Optional.of(quarkusTestRunnerDescriptorWriter.write(new QuarkusTestRunnerRequest(
                    projectDirectory,
                    compileResult.buildResult().outputDirectory(),
                    compileResult.outputDirectory(),
                    modelPath,
                    projectDirectory.resolve("target/quarkus/zolt-bootstrap.properties"),
                    runnerClasspath,
                    runnerClasspath.stream().anyMatch(TestRunService::isJbossLogManagerJar))));
        } catch (QuarkusAugmentationException exception) {
            throw new TestRunException(
                    "Could not write Quarkus test runner descriptor. "
                            + "Clean target/quarkus, run `zolt build`, then run `zolt test` again. "
                            + exception.getMessage(),
                    exception);
        }
    }

    static void failOnHiddenQuarkusBootstrapFailure(ProjectConfig config, String output) {
        if (!config.frameworkSettings().quarkus().enabled()) {
            return;
        }
        if (output == null || output.isBlank()) {
            return;
        }
        if (!hiddenQuarkusBootstrapFailure(output)) {
            return;
        }
        throw new TestRunException(
                "Quarkus test bootstrap failed while JUnit Platform reported success. "
                        + "Zolt supports an early Quarkus test runner path, but this project hit an unsupported "
                        + "Quarkus test bootstrap shape. Use plain JUnit tests for now, or simplify `@QuarkusTest` "
                        + "usage until Zolt's dedicated Quarkus test runner is expanded.\n"
                        + output.stripTrailing());
    }

    private static boolean hiddenQuarkusBootstrapFailure(String output) {
        return output.contains("io.quarkus.test.junit")
                && (output.contains("io.quarkus.bootstrap.BootstrapException")
                        || output.contains("ClassCastException: class io.quarkus.builder.BuildChainBuilder")
                        || output.contains("NullPointerException"));
    }

    private static boolean isJbossLogManagerJar(Path path) {
        String name = path.getFileName() == null ? "" : path.getFileName().toString();
        return name.startsWith("jboss-logmanager-") && name.endsWith(".jar");
    }

    private static List<Path> currentWorkerClasspath() {
        String classpath = System.getProperty("java.class.path", "");
        List<Path> entries = Arrays.stream(classpath.split(java.io.File.pathSeparator))
                .filter(entry -> !entry.isBlank())
                .map(Path::of)
                .toList();
        if (entries.isEmpty()) {
            throw new TestRunException(
                    "Could not determine Zolt worker classpath for Quarkus tests. "
                            + "Run zolt test from the packaged launcher or check java.class.path.");
        }
        return entries;
    }

    @FunctionalInterface
    interface QuarkusTestApplicationModelWriter {
        Optional<Path> write(Path projectDirectory, ProjectConfig config);
    }

    @FunctionalInterface
    interface QuarkusTestWorkerRunner {
        String run(Path javaExecutable, List<Path> workerClasspath, QuarkusTestRunnerDescriptor descriptor);
    }
}
