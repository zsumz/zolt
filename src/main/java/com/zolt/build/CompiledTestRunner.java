package com.zolt.build;

import com.zolt.classpath.Classpath;
import com.zolt.classpath.ClasspathSet;
import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkStatus;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.junit.JunitWorkerClientException;
import com.zolt.junit.JunitWorkerProcess;
import com.zolt.junit.JunitWorkerProcessLauncher;
import com.zolt.project.ProjectConfig;
import com.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

final class CompiledTestRunner {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JUNIT_CONSOLE_RUNNER = "junit-console";
    private static final String PLAIN_JUNIT_WORKER_RUNNER = "zolt-junit-worker";

    private final JdkChecker jdkDetector;
    private final JavaRunner javaRunner;
    private final FrameworkTestRunner frameworkTestRunner;
    private final Supplier<List<Path>> plainJunitWorkerClasspath;
    private final TestRunService.PlainJunitWorkerRunner plainJunitWorkerRunner;
    private final JunitConsoleArguments junitConsoleArguments;
    private final TestRuntimeInputBuilder testRuntimeInputBuilder = new TestRuntimeInputBuilder();
    private final JunitLauncherClasspath junitLauncherClasspath = new JunitLauncherClasspath();
    private final TestConsoleFailureHandler consoleFailureHandler = new TestConsoleFailureHandler();
    private final FrameworkTestSelectionMapper frameworkTestSelectionMapper = new FrameworkTestSelectionMapper();
    private final boolean plainJunitWorkerEnabled;

    CompiledTestRunner(
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            FrameworkTestRunner frameworkTestRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            TestRunService.PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled,
            String pathSeparator) {
        this.jdkDetector = jdkDetector;
        this.javaRunner = javaRunner;
        this.frameworkTestRunner = frameworkTestRunner;
        this.plainJunitWorkerClasspath = plainJunitWorkerClasspath;
        this.plainJunitWorkerRunner = plainJunitWorkerRunner;
        this.junitConsoleArguments = new JunitConsoleArguments(pathSeparator);
        this.plainJunitWorkerEnabled = plainJunitWorkerEnabled;
    }

    TestRunResult run(
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
            TestRunService.PlainJunitWorkerRunResult result = plainJunitWorkerRunner.run(
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

    static TestRunService.PlainJunitWorkerRunResult runPlainJunitWorker(
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
            return new TestRunService.PlainJunitWorkerRunResult(result, startupNanos, requestNanos);
        } catch (JunitWorkerClientException exception) {
            throw new TestRunException(exception.getMessage(), exception);
        }
    }

    private static List<Path> absolutePaths(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }
}
