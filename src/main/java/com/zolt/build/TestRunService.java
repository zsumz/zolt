package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.junit.JunitWorkerClientException;
import com.zolt.junit.JunitWorkerProcess;
import com.zolt.junit.JunitWorkerProcessLauncher;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestRuntimeSettings;
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
import java.util.Collections;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.StringJoiner;
import java.util.function.Supplier;

public final class TestRunService {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JUNIT_CONSOLE_RUNNER = "junit-console";
    private static final String PLAIN_JUNIT_WORKER_RUNNER = "zolt-junit-worker";
    private static final String QUARKUS_TEST_WORKER_RUNNER = "quarkus-test-worker";
    private static final String JBOSS_LOG_MANAGER_PROPERTY =
            "-Djava.util.logging.manager=org.jboss.logmanager.LogManager";

    private final TestCompileService testCompileService;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;
    private final QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter;
    private final QuarkusTestRunnerDescriptorWriter quarkusTestRunnerDescriptorWriter;
    private final Supplier<List<Path>> quarkusTestWorkerClasspath;
    private final QuarkusTestWorkerRunner quarkusTestWorkerRunner;
    private final Supplier<List<Path>> plainJunitWorkerClasspath;
    private final PlainJunitWorkerRunner plainJunitWorkerRunner;
    private final boolean plainJunitWorkerEnabled;
    private final String pathSeparator;

    public TestRunService() {
        this(new JdkDetector());
    }

    public TestRunService(JdkChecker jdkDetector) {
        this(
                new TestCompileService(jdkDetector),
                jdkDetector,
                new JavaRunner(),
                new QuarkusTestApplicationModelService()::writeIfEnabled,
                new QuarkusTestRunnerDescriptorWriter(),
                TestRunService::currentWorkerClasspath,
                (javaExecutable, workerClasspath, descriptor) ->
                        new QuarkusTestWorkerLauncher(javaExecutable, workerClasspath).run(descriptor),
                TestRunService::currentWorkerClasspath,
                TestRunService::runPlainJunitWorker,
                Boolean.getBoolean("zolt.junit.worker"),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            QuarkusTestRunnerDescriptorWriter quarkusTestRunnerDescriptorWriter,
            Supplier<List<Path>> quarkusTestWorkerClasspath,
            QuarkusTestWorkerRunner quarkusTestWorkerRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.quarkusTestApplicationModelWriter = quarkusTestApplicationModelWriter;
        this.quarkusTestRunnerDescriptorWriter = quarkusTestRunnerDescriptorWriter;
        this.quarkusTestWorkerClasspath = quarkusTestWorkerClasspath;
        this.quarkusTestWorkerRunner = quarkusTestWorkerRunner;
        this.plainJunitWorkerClasspath = plainJunitWorkerClasspath;
        this.plainJunitWorkerRunner = plainJunitWorkerRunner;
        this.plainJunitWorkerEnabled = plainJunitWorkerEnabled;
        this.pathSeparator = pathSeparator;
    }

    public TestRunResult runTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return runTests(projectDirectory, config, cacheRoot, TestSelection.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection) {
        return runTests(projectDirectory, config, cacheRoot, selection, TestJvmArguments.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, TestReportSettings.disabled());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        TestCompileResultWithClasspaths compileResult =
                compileTests(projectDirectory, config, cacheRoot);
        return runCompiledTests(
                projectDirectory,
                config,
                compileResult.classpaths(),
                compileResult.testCompileResult(),
                selection,
                jvmArguments,
                reportSettings);
    }

    public TestCompileResultWithClasspaths compileTests(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return testCompileService.compileTestsWithClasspaths(projectDirectory, config, cacheRoot);
    }

    public BuildResultWithClasspaths buildTestInputs(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return testCompileService.buildTestInputs(projectDirectory, config, cacheRoot);
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, TestSelection.empty());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, TestJvmArguments.empty());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, TestReportSettings.disabled());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings);
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult) {
        return runTests(projectDirectory, config, classpaths, buildResult, TestSelection.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection) {
        return runTests(projectDirectory, config, classpaths, buildResult, selection, TestJvmArguments.empty());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runTests(projectDirectory, config, classpaths, buildResult, selection, jvmArguments, TestReportSettings.disabled());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        TestCompileResult compileResult = compileTests(projectDirectory, config, classpaths, buildResult);
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings);
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
            TestCompileResult compileResult,
            TestSelection selection) {
        return runTests(projectDirectory, config, classpaths, compileResult, selection, TestJvmArguments.empty());
    }

    private TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments) {
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, TestReportSettings.disabled());
    }

    private TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings) {
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        TestReportSettings testReportSettings = reportSettings == null ? TestReportSettings.disabled() : reportSettings;
        Optional<Path> reportsDirectory = testReportSettings.absoluteReportsDirectory(projectDirectory);
        TestRuntimeInputs testRuntime = testRuntimeInputs(
                projectDirectory,
                config.build().testRuntime(),
                jvmArguments);
        TestJvmArguments testJvmArguments = testRuntime.jvmArguments();
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
                    serializedApplicationModel,
                    testSelection,
                    testRuntime);
        List<Path> launcherClasspath = junitLauncherClasspath(runnerClasspath);
        if (config.frameworkSettings().quarkus().enabled()) {
            if (reportsDirectory.isPresent()) {
                throw new TestRunException(
                        "JUnit XML reports are not supported by the Quarkus plain-JUnit worker path yet. "
                                + "Run without --reports-dir or use the JUnit Console path for this project.");
            }
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
            return new TestRunResult(
                    compileResult,
                    output,
                    QUARKUS_TEST_WORKER_RUNNER,
                    runnerClasspath.size(),
                    workerClasspath.size(),
                    1,
                    -1L,
                    -1L,
                    testSelection,
                    testJvmArguments,
                    reportsDirectory);
        }
        if (plainJunitWorkerEnabled) {
            if (reportsDirectory.isPresent()) {
                throw new TestRunException(
                        "JUnit XML reports are not supported by the opt-in Zolt JUnit worker path yet. "
                                + "Run without --reports-dir or unset zolt.junit.worker.");
            }
            List<Path> workerClasspath = plainJunitWorkerClasspath.get();
            PlainJunitWorkerRunResult result = plainJunitWorkerRunner.run(
                    jdkStatus.java().orElseThrow(),
                    workerClasspath,
                    projectDirectory,
                    runnerClasspath,
                    compileResult.outputDirectory().toAbsolutePath().normalize(),
                    testSelection,
                    testJvmArguments,
                    testRuntime.environment());
            failOnHiddenQuarkusBootstrapFailure(config, result.workerResult().output());
            if (result.workerResult().exitCode() != 0) {
                throw new TestRunException(
                        "JUnit worker tests failed with exit code "
                                + result.workerResult().exitCode()
                                + ". Fix failing tests, then run `zolt test` again.\n"
                                + result.workerResult().output().stripTrailing());
            }
            return new TestRunResult(
                    compileResult,
                    result.workerResult().output(),
                    PLAIN_JUNIT_WORKER_RUNNER,
                    runnerClasspath.size(),
                    workerClasspath.size() + runnerClasspath.size(),
                    1,
                    result.startupNanos(),
                    result.requestNanos(),
                    testSelection,
                    testJvmArguments,
                    reportsDirectory);
        }
        JavaRunResult result;
        try {
            result = javaRunner.run(
                    jdkStatus.java().orElseThrow(),
                    new Classpath(launcherClasspath),
                    CONSOLE_MAIN_CLASS,
                    jvmArguments(projectDirectory, runnerClasspath, serializedApplicationModel, testJvmArguments),
                    consoleArguments(runnerClasspath, compileResult.outputDirectory(), testSelection, reportsDirectory),
                    testRuntime.environment());
        } catch (JavaRunException exception) {
            if (!testSelection.emptySelection() && noTestsFound(exception.getMessage())) {
                throw noSelectedTestsMatched(exception.getMessage(), exception);
            }
            if (reportsDirectory.isEmpty()) {
                throw exception;
            }
            throw testFailed(exception, reportsDirectory);
        }
        if (!testSelection.emptySelection() && noTestsFound(result.output())) {
            throw noSelectedTestsMatched(result.output(), null);
        }
        failOnHiddenQuarkusBootstrapFailure(config, result.output());
        return new TestRunResult(
                compileResult,
                result.output(),
                JUNIT_CONSOLE_RUNNER,
                runnerClasspath.size(),
                launcherClasspath.size(),
                1,
                -1L,
                -1L,
                testSelection,
                testJvmArguments,
                reportsDirectory);
    }

    private List<String> consoleArguments(
            List<Path> runnerClasspath,
            Path testOutputDirectory,
            TestSelection selection,
            Optional<Path> reportsDirectory) {
        List<String> arguments = new ArrayList<>();
        arguments.add("execute");
        arguments.add("--disable-banner");
        arguments.add("--class-path");
        arguments.add(joined(runnerClasspath));
        addConsoleSelectors(arguments, testOutputDirectory.toAbsolutePath().normalize(), selection);
        reportsDirectory.ifPresent(directory -> {
            arguments.add("--reports-dir");
            arguments.add(directory.toString());
        });
        arguments.add("--details");
        arguments.add("summary");
        return List.copyOf(arguments);
    }

    private static void addConsoleSelectors(
            List<String> arguments,
            Path testOutputDirectory,
            TestSelection selection) {
        boolean hasClassOrMethodSelectors =
                !selection.classSelectors().isEmpty() || !selection.methodSelectors().isEmpty();
        if (!hasClassOrMethodSelectors) {
            arguments.add("--scan-class-path=" + testOutputDirectory);
        }
        for (String classSelector : selection.classSelectors()) {
            arguments.add("--select-class");
            arguments.add(classSelector);
        }
        for (TestSelection.MethodSelector methodSelector : selection.methodSelectors()) {
            arguments.add("--select-method");
            arguments.add(methodSelector.className() + "#" + methodSelector.methodName());
        }
        List<String> classNamePatterns = selection.classNamePatterns().isEmpty() && !hasClassOrMethodSelectors
                ? TestSelection.defaultScanClassNamePatterns()
                : selection.classNameRegexPatterns();
        for (String pattern : classNamePatterns) {
            arguments.add("--include-classname");
            arguments.add(pattern);
        }
        for (String tag : selection.includedTags()) {
            arguments.add("--include-tag");
            arguments.add(tag);
        }
        for (String tag : selection.excludedTags()) {
            arguments.add("--exclude-tag");
            arguments.add(tag);
        }
    }

    private static boolean noTestsFound(String message) {
        return message.contains("No tests found")
                || message.contains("Tests found: 0")
                || message.contains("[         0 tests found");
    }

    private static TestRunException noSelectedTestsMatched(String output, Throwable cause) {
        String message = "Selected tests did not match any tests. "
                + "Check --test, --tests, --include-tag, and --exclude-tag values, then run `zolt test` again.\n"
                + output.stripTrailing();
        return cause == null ? new TestRunException(message) : new TestRunException(message, cause);
    }

    private static TestRunException testFailed(JavaRunException exception, Optional<Path> reportsDirectory) {
        String message = exception.getMessage();
        if (reportsDirectory.isPresent()) {
            message = message + "\nTest reports: " + reportsDirectory.orElseThrow();
        }
        return new TestRunException(message, exception);
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

    private static PlainJunitWorkerRunResult runPlainJunitWorker(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestJvmArguments jvmArguments,
            Map<String, String> environment) {
        long startupStarted = System.nanoTime();
        try (JunitWorkerProcess process = new JunitWorkerProcessLauncher(javaExecutable, workerClasspath)
                .start(projectDirectory, testRuntimeClasspath, jvmArguments.values(), environment)) {
            long startupNanos = System.nanoTime() - startupStarted;
            long requestStarted = System.nanoTime();
            JunitWorkerClient.WorkerRunResult result = process.run(testOutputDirectory, testSelection);
            long requestNanos = System.nanoTime() - requestStarted;
            return new PlainJunitWorkerRunResult(result, startupNanos, requestNanos);
        } catch (JunitWorkerClientException exception) {
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
            Optional<Path> serializedApplicationModel,
            TestJvmArguments testJvmArguments) {
        List<String> arguments = new ArrayList<>();
        arguments.addAll(testJvmArguments.values());
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

    private static TestRuntimeInputs testRuntimeInputs(
            Path projectDirectory,
            TestRuntimeSettings settings,
            TestJvmArguments cliJvmArguments) {
        TestRuntimeSettings testRuntimeSettings = settings == null ? TestRuntimeSettings.defaults() : settings;
        TestJvmArguments commandLineArguments = cliJvmArguments == null ? TestJvmArguments.empty() : cliJvmArguments;
        List<String> arguments = new ArrayList<>();
        for (String argument : testRuntimeSettings.jvmArgs()) {
            arguments.add(expandProjectRoot(projectDirectory, argument, "test.runtime.jvmArgs"));
        }
        for (Map.Entry<String, String> entry : testRuntimeSettings.systemProperties().entrySet()) {
            arguments.add("-D"
                    + entry.getKey()
                    + "="
                    + expandProjectRoot(projectDirectory, entry.getValue(), "test.runtime.systemProperties"));
        }
        arguments.addAll(commandLineArguments.values());

        Map<String, String> environment = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : testRuntimeSettings.environment().entrySet()) {
            environment.put(
                    entry.getKey(),
                    expandProjectRoot(projectDirectory, entry.getValue(), "test.runtime.environment"));
        }
        return new TestRuntimeInputs(new TestJvmArguments(arguments), Collections.unmodifiableMap(environment));
    }

    private static String expandProjectRoot(Path projectDirectory, String value, String section) {
        String projectRoot = projectDirectory.toAbsolutePath().normalize().toString();
        String expanded = value.replace("${project.root}", projectRoot);
        if (expanded.contains("${")) {
            throw new TestRunException(
                    "Unsupported placeholder in ["
                            + section
                            + "] value `"
                            + value
                            + "`. Supported placeholder: ${project.root}.");
        }
        return expanded;
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
            Optional<Path> serializedApplicationModel,
            TestSelection testSelection,
            TestRuntimeInputs testRuntime) {
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
                    runnerClasspath.stream().anyMatch(TestRunService::isJbossLogManagerJar),
                    testSelection,
                    testRuntime.jvmArguments(),
                    testRuntime.environment())));
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
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
        if (entries.isEmpty()) {
            throw new TestRunException(
                    "Could not determine Zolt worker classpath for test execution. "
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

    @FunctionalInterface
    interface PlainJunitWorkerRunner {
        PlainJunitWorkerRunResult run(
                Path javaExecutable,
                List<Path> workerClasspath,
                Path projectDirectory,
                List<Path> testRuntimeClasspath,
                Path testOutputDirectory,
                TestSelection testSelection,
                TestJvmArguments jvmArguments,
                Map<String, String> environment);
    }

    record PlainJunitWorkerRunResult(
            JunitWorkerClient.WorkerRunResult workerResult,
            long startupNanos,
            long requestNanos) {
    }

    record TestRuntimeInputs(
            TestJvmArguments jvmArguments,
            Map<String, String> environment) {
    }
}
