package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.junit.JunitWorkerClientException;
import com.zolt.junit.JunitWorkerProcess;
import com.zolt.junit.JunitWorkerProcessLauncher;
import com.zolt.project.ProjectConfig;
import com.zolt.classpath.Classpath;
import com.zolt.resolve.ResolveService;
import com.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

public final class TestRunService {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JUNIT_CONSOLE_RUNNER = "junit-console";
    private static final String PLAIN_JUNIT_WORKER_RUNNER = "zolt-junit-worker";

    private final TestCompileService testCompileService;
    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;
    private final FrameworkTestRunner frameworkTestRunner;
    private final Supplier<List<Path>> plainJunitWorkerClasspath;
    private final PlainJunitWorkerRunner plainJunitWorkerRunner;
    private final JunitConsoleArguments junitConsoleArguments;
    private final TestRuntimeInputBuilder testRuntimeInputBuilder = new TestRuntimeInputBuilder();
    private final JunitLauncherClasspath junitLauncherClasspath = new JunitLauncherClasspath();
    private final TestConsoleFailureHandler consoleFailureHandler = new TestConsoleFailureHandler();
    private final FrameworkTestSelectionMapper frameworkTestSelectionMapper = new FrameworkTestSelectionMapper();
    private final boolean plainJunitWorkerEnabled;

    public TestRunService() {
        this(new JdkDetector());
    }

    public TestRunService(JdkChecker jdkDetector) {
        this(jdkDetector, FrameworkTestRunner.none());
    }

    public TestRunService(FrameworkTestRunner frameworkTestRunner) {
        this(new JdkDetector(), frameworkTestRunner);
    }

    public TestRunService(JdkChecker jdkDetector, FrameworkTestRunner frameworkTestRunner) {
        this(jdkDetector, frameworkTestRunner, new ResolveService());
    }

    public TestRunService(FrameworkTestRunner frameworkTestRunner, ResolveService resolveService) {
        this(new JdkDetector(), frameworkTestRunner, resolveService);
    }

    public TestRunService(
            JdkChecker jdkDetector,
            FrameworkTestRunner frameworkTestRunner,
            ResolveService resolveService) {
        this(
                new TestCompileService(jdkDetector, resolveService),
                jdkDetector,
                new JavaRunner(),
                frameworkTestRunner,
                new CurrentWorkerClasspath()::discover,
                TestRunService::runPlainJunitWorker,
                Boolean.getBoolean("zolt.junit.worker"),
                java.io.File.pathSeparator);
    }

    TestRunService(
            TestCompileService testCompileService,
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            FrameworkTestRunner frameworkTestRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled,
            String pathSeparator) {
        this.testCompileService = testCompileService;
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.frameworkTestRunner = frameworkTestRunner;
        this.plainJunitWorkerClasspath = plainJunitWorkerClasspath;
        this.plainJunitWorkerRunner = plainJunitWorkerRunner;
        this.junitConsoleArguments = new JunitConsoleArguments(pathSeparator);
        this.plainJunitWorkerEnabled = plainJunitWorkerEnabled;
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
        return runTests(projectDirectory, config, cacheRoot, selection, jvmArguments, reportSettings, List.of());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        TestCompileResultWithClasspaths compileResult =
                compileTests(projectDirectory, config, cacheRoot);
        return runCompiledTests(
                projectDirectory,
                config,
                compileResult.classpaths(),
                compileResult.testCompileResult(),
                selection,
                jvmArguments,
                reportSettings,
                cliEvents);
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
        return runCompiledTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, List.of());
    }

    public TestRunResult runCompiledTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, cliEvents);
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
        return runTests(projectDirectory, config, classpaths, buildResult, selection, jvmArguments, reportSettings, List.of());
    }

    public TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            BuildResult buildResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        TestCompileResult compileResult = compileTests(projectDirectory, config, classpaths, buildResult);
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, cliEvents);
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
        return runTests(projectDirectory, config, classpaths, compileResult, selection, jvmArguments, reportSettings, List.of());
    }

    private TestRunResult runTests(
            Path projectDirectory,
            ProjectConfig config,
            ClasspathSet classpaths,
            TestCompileResult compileResult,
            TestSelection selection,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        TestReportSettings testReportSettings = reportSettings == null ? TestReportSettings.disabled() : reportSettings;
        Optional<Path> reportsDirectory = testReportSettings.absoluteReportsDirectory(projectDirectory);
        TestRuntimeInputs testRuntime = testRuntimeInputBuilder.build(
                projectDirectory,
                config.build().testRuntime(),
                jvmArguments,
                cliEvents);
        TestJvmArguments testJvmArguments = testRuntime.jvmArguments();
        List<Path> runnerClasspath = new ArrayList<>();
        runnerClasspath.add(compileResult.outputDirectory());
        runnerClasspath.add(compileResult.buildResult().outputDirectory());
        runnerClasspath.addAll(classpaths.test().entries());
        runnerClasspath = absolutePaths(runnerClasspath);
        if (!junitLauncherClasspath.hasConsoleJar(runnerClasspath)) {
            throw new TestRunException(
                    "JUnit Platform Console is not present on the test classpath. "
                            + "Run `zolt resolve` to refresh Zolt's test runner tooling. "
                            + "Keep JUnit, Spock, and other test engines declared in [test.dependencies].");
        }

        JdkStatus jdkStatus = jdkDetector.detect(config.project().java());
        if (!jdkStatus.ok()) {
            throw new BuildException("JDK check failed. " + String.join(" ", jdkStatus.problems()));
        }
        List<Path> launcherClasspath = junitLauncherClasspath.launcherClasspath(runnerClasspath);
        if (frameworkTestRunner.isEnabled(config)) {
            if (reportsDirectory.isPresent()) {
                frameworkTestRunner.unsupportedReportsMessage()
                        .ifPresent(message -> {
                            throw new TestRunException(message);
                        });
            }
            FrameworkTestRunResult frameworkResult = frameworkTestRunner.runIfEnabled(new FrameworkTestRunRequest(
                            projectDirectory,
                            config,
                            compileResult.buildResult().outputDirectory(),
                            compileResult.outputDirectory(),
                            runnerClasspath,
                            jdkStatus.java().orElseThrow(),
                            frameworkTestSelectionMapper.map(testSelection),
                            testJvmArguments.values(),
                            testRuntime.environment()))
                    .orElseThrow(() -> new TestRunException(
                            "Framework test runner was not configured for the enabled framework."));
            String output = frameworkResult.output();
            return new TestRunResult(
                    compileResult,
                    output,
                    frameworkTestRunner.testRunnerName(),
                    runnerClasspath.size(),
                    frameworkResult.workerClasspathEntries(),
                    frameworkResult.discoveryScanRoots(),
                    -1L,
                    -1L,
                    testSelection,
                    testJvmArguments,
                    reportsDirectory);
        }
        if (plainJunitWorkerEnabled) {
            List<Path> workerClasspath = plainJunitWorkerClasspath.get();
            PlainJunitWorkerRunResult result = plainJunitWorkerRunner.run(
                    jdkStatus.java().orElseThrow(),
                    workerClasspath,
                    projectDirectory,
                    runnerClasspath,
                    compileResult.outputDirectory().toAbsolutePath().normalize(),
                    testSelection,
                    testJvmArguments,
                    testRuntime.environment(),
                    reportsDirectory,
                    testRuntime.events());
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
                    junitLauncherClasspath.jvmArguments(projectDirectory, runnerClasspath, testJvmArguments),
                    junitConsoleArguments.arguments(
                            runnerClasspath,
                            compileResult.outputDirectory(),
                            testSelection,
                            reportsDirectory,
                            testRuntime.events()),
                    testRuntime.environment());
        } catch (JavaRunException exception) {
            consoleFailureHandler.throwForFailedRun(exception, testSelection, reportsDirectory);
            throw exception;
        }
        consoleFailureHandler.throwIfSelectedTestsDidNotMatch(result.output(), testSelection);
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

    private static List<Path> absolutePaths(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private static PlainJunitWorkerRunResult runPlainJunitWorker(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events) {
        long startupStarted = System.nanoTime();
        try (JunitWorkerProcess process = new JunitWorkerProcessLauncher(javaExecutable, workerClasspath)
                .start(projectDirectory, testRuntimeClasspath, jvmArguments.values(), environment)) {
            long startupNanos = System.nanoTime() - startupStarted;
            long requestStarted = System.nanoTime();
            JunitWorkerClient.WorkerRunResult result = process.run(
                    testOutputDirectory,
                    testSelection,
                    reportsDirectory,
                    events);
            long requestNanos = System.nanoTime() - requestStarted;
            return new PlainJunitWorkerRunResult(result, startupNanos, requestNanos);
        } catch (JunitWorkerClientException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
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
                Map<String, String> environment,
                Optional<Path> reportsDirectory,
                List<String> events);
    }

    record PlainJunitWorkerRunResult(
            JunitWorkerClient.WorkerRunResult workerResult,
            long startupNanos,
            long requestNanos) {
    }

}
