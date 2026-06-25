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
import com.zolt.test.TestInventoryEntry;
import com.zolt.test.TestSelection;
import com.zolt.test.TestWorkerPoolPlan;
import com.zolt.test.TestWorkerPoolWave;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

final class CompiledTestRunner {
    private static final String CONSOLE_MAIN_CLASS = "org.junit.platform.console.ConsoleLauncher";
    private static final String JUNIT_CONSOLE_RUNNER = "junit-console";
    private static final String PLAIN_JUNIT_WORKER_RUNNER = "zolt-junit-worker";

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

    CompiledTestRunner(
            JdkChecker jdkDetector,
            JavaRunner javaRunner,
            FrameworkTestRunner frameworkTestRunner,
            Supplier<List<Path>> plainJunitWorkerClasspath,
            PlainJunitWorkerRunner plainJunitWorkerRunner,
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
            TestWorkerPoolPlan workerPoolPlan,
            TestJvmArguments jvmArguments,
            TestReportSettings reportSettings,
            List<String> cliEvents) {
        TestSelection testSelection = selection == null ? TestSelection.empty() : selection;
        TestWorkerPoolPlan testWorkerPoolPlan = workerPoolPlan == null
                ? new TestWorkerPoolPlan(false, 1, List.of())
                : workerPoolPlan;
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
            long requestStarted = System.nanoTime();
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
            long requestNanos = System.nanoTime() - requestStarted;
            String output = frameworkResult.output();
            return new TestRunResult(
                    compileResult,
                    output,
                    frameworkTestRunner.testRunnerName(),
                    runnerClasspath.size(),
                    frameworkResult.workerClasspathEntries(),
                    frameworkResult.discoveryScanRoots(),
                    -1L,
                    requestNanos,
                    testSelection,
                    testJvmArguments,
                    reportsDirectory);
        }
        if (plainJunitWorkerEnabled) {
            List<Path> workerClasspath = plainJunitWorkerClasspath.get();
            if (testWorkerPoolPlan.enabled() && !testWorkerPoolPlan.empty()) {
                WorkerPoolRunResult poolResult = runPlainJunitWorkerPool(
                        jdkStatus.java().orElseThrow(),
                        workerClasspath,
                        projectDirectory,
                        config,
                        runnerClasspath,
                        compileResult.outputDirectory().toAbsolutePath().normalize(),
                        testSelection,
                        testWorkerPoolPlan,
                        testJvmArguments,
                        testRuntime.environment(),
                        reportsDirectory,
                        testRuntime.events());
                return new TestRunResult(
                        compileResult,
                        poolResult.output(),
                        PLAIN_JUNIT_WORKER_RUNNER,
                        runnerClasspath.size(),
                        workerClasspath.size() + runnerClasspath.size(),
                        poolResult.workerRequests(),
                        poolResult.startupNanos(),
                        poolResult.requestNanos(),
                        testSelection,
                        testJvmArguments,
                        reportsDirectory);
            }
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
        long requestStarted = System.nanoTime();
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
        long requestNanos = System.nanoTime() - requestStarted;
        consoleFailureHandler.throwIfSelectedTestsDidNotMatch(result.output(), testSelection);
        return new TestRunResult(
                compileResult,
                result.output(),
                JUNIT_CONSOLE_RUNNER,
                runnerClasspath.size(),
                launcherClasspath.size(),
                1,
                -1L,
                requestNanos,
                testSelection,
                testJvmArguments,
                reportsDirectory);
    }

    private WorkerPoolRunResult runPlainJunitWorkerPool(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            ProjectConfig config,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestWorkerPoolPlan workerPoolPlan,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events) {
        ExecutorService executor = Executors.newFixedThreadPool(workerPoolPlan.maxWorkers());
        StringBuilder output = new StringBuilder();
        long startupNanos = 0L;
        long requestStarted = System.nanoTime();
        int workerRequests = 0;
        List<String> workerIds = workerIds(workerPoolPlan);
        writeWorkerEvidenceManifests(reportsDirectory, jvmArguments, workerIds);
        try {
            for (int waveIndex = 0; waveIndex < workerPoolPlan.waves().size(); waveIndex++) {
                TestWorkerPoolWave wave = workerPoolPlan.waves().get(waveIndex);
                List<Future<WorkerTaskResult>> futures = new ArrayList<>();
                for (int entryIndex = 0; entryIndex < wave.entries().size(); entryIndex++) {
                    TestInventoryEntry entry = wave.entries().get(entryIndex);
                    String workerId = "wave-" + (waveIndex + 1) + "-worker-" + (entryIndex + 1);
                    futures.add(executor.submit(workerTask(
                            javaExecutable,
                            workerClasspath,
                            projectDirectory,
                            config,
                            testRuntimeClasspath,
                            testOutputDirectory,
                            testSelection,
                            entry,
                            jvmArguments,
                            environment,
                            reportsDirectory,
                            events,
                            workerId)));
                }
                for (Future<WorkerTaskResult> future : futures) {
                    WorkerTaskResult taskResult = getWorkerTask(future);
                    workerRequests++;
                    startupNanos += taskResult.result().startupNanos();
                    output.append(taskResult.result().workerResult().output());
                    if (taskResult.result().workerResult().exitCode() != 0) {
                        throw new TestRunException(
                                "JUnit worker tests failed with exit code "
                                        + taskResult.result().workerResult().exitCode()
                                        + " in "
                                        + taskResult.className()
                                        + ". Fix failing tests, then run `zolt test` again.\n"
                                        + taskResult.result().workerResult().output().stripTrailing());
                    }
                }
            }
            return new WorkerPoolRunResult(
                    output.toString(),
                    workerRequests,
                    startupNanos,
                    System.nanoTime() - requestStarted);
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<String> workerIds(TestWorkerPoolPlan workerPoolPlan) {
        List<String> workerIds = new ArrayList<>();
        for (int waveIndex = 0; waveIndex < workerPoolPlan.waves().size(); waveIndex++) {
            TestWorkerPoolWave wave = workerPoolPlan.waves().get(waveIndex);
            for (int entryIndex = 0; entryIndex < wave.entries().size(); entryIndex++) {
                workerIds.add("wave-" + (waveIndex + 1) + "-worker-" + (entryIndex + 1));
            }
        }
        return List.copyOf(workerIds);
    }

    private static void writeWorkerEvidenceManifests(
            Optional<Path> reportsDirectory,
            TestJvmArguments jvmArguments,
            List<String> workerIds) {
        reportsDirectory.ifPresent(directory -> writeWorkerEvidenceManifest(directory.resolve("workers").resolve("zolt-workers.json"), workerIds));
        jacocoExecFile(jvmArguments)
                .map(Path::getParent)
                .ifPresent(directory -> writeWorkerEvidenceManifest(directory.resolve("workers").resolve("zolt-workers.json"), workerIds));
    }

    private static void writeWorkerEvidenceManifest(Path manifest, List<String> workerIds) {
        try {
            Files.createDirectories(manifest.getParent());
            Files.writeString(manifest, workerEvidenceJson(workerIds));
        } catch (IOException exception) {
            throw new TestRunException("Could not write test worker evidence manifest to " + manifest + ".", exception);
        }
    }

    private static String workerEvidenceJson(List<String> workerIds) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"version\": 1,\n");
        json.append("  \"workers\": [\n");
        for (int index = 0; index < workerIds.size(); index++) {
            json.append("    \"").append(workerIds.get(index)).append("\"");
            if (index + 1 < workerIds.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private Callable<WorkerTaskResult> workerTask(
            Path javaExecutable,
            List<Path> workerClasspath,
            Path projectDirectory,
            ProjectConfig config,
            List<Path> testRuntimeClasspath,
            Path testOutputDirectory,
            TestSelection testSelection,
            TestInventoryEntry entry,
            TestJvmArguments jvmArguments,
            Map<String, String> environment,
            Optional<Path> reportsDirectory,
            List<String> events,
            String workerId) {
        return () -> {
            PlainJunitWorkerRunResult result = plainJunitWorkerRunner.run(
                    javaExecutable,
                    workerClasspath,
                    projectDirectory,
                    testRuntimeClasspath,
                    testOutputDirectory,
                    workerSelection(testSelection, entry),
                    workerJvmArguments(jvmArguments, workerId),
                    workerEnvironment(projectDirectory, config, environment, jvmArguments, workerId),
                    workerReportsDirectory(reportsDirectory, workerId),
                    events);
            return new WorkerTaskResult(entry.className(), result);
        };
    }

    private WorkerTaskResult getWorkerTask(Future<WorkerTaskResult> future) {
        try {
            return future.get();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new TestRunException("JUnit worker pool was interrupted while waiting for test results.", exception);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause();
            if (cause instanceof TestRunException testRunException) {
                throw testRunException;
            }
            throw new TestRunException("JUnit worker pool failed while running tests.", cause);
        }
    }

    private static TestSelection workerSelection(TestSelection selection, TestInventoryEntry entry) {
        List<TestSelection.MethodSelector> methodSelectors = selection.methodSelectors().stream()
                .filter(method -> method.className().equals(entry.className()))
                .toList();
        List<String> classSelectors = methodSelectors.isEmpty()
                ? List.of(entry.className())
                : List.of();
        return TestSelection.fromFields(
                classSelectors,
                methodSelectors,
                List.of(),
                selection.includedTags(),
                selection.excludedTags());
    }

    private static Map<String, String> workerEnvironment(
            Path projectDirectory,
            ProjectConfig config,
            Map<String, String> environment,
            TestJvmArguments jvmArguments,
            String workerId) {
        Map<String, String> values = new LinkedHashMap<>(environment);
        Path outputDirectory = projectDirectory.resolve(config.build().outputRoot())
                .resolve("test-workers")
                .resolve(workerId)
                .toAbsolutePath()
                .normalize();
        values.put("ZOLT_TEST_WORKER_ID", workerId);
        values.put("ZOLT_TEST_WORKER_OUTPUT_DIR", outputDirectory.toString());
        jacocoWorkerExecFile(jvmArguments, workerId)
                .ifPresent(path -> values.put("ZOLT_COVERAGE_EXEC_FILE", path.toString()));
        return Map.copyOf(values);
    }

    private static Optional<Path> workerReportsDirectory(Optional<Path> reportsDirectory, String workerId) {
        return reportsDirectory.map(directory -> directory.resolve("workers").resolve(workerId));
    }

    private static TestJvmArguments workerJvmArguments(TestJvmArguments jvmArguments, String workerId) {
        List<String> values = jvmArguments.values().stream()
                .map(argument -> rewriteJacocoDestfile(argument, workerId).orElse(argument))
                .toList();
        return new TestJvmArguments(values);
    }

    private static Optional<Path> jacocoWorkerExecFile(TestJvmArguments jvmArguments, String workerId) {
        return jvmArguments.values().stream()
                .map(argument -> jacocoWorkerExecFile(argument, workerId))
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<Path> jacocoExecFile(TestJvmArguments jvmArguments) {
        return jvmArguments.values().stream()
                .map(CompiledTestRunner::jacocoExecFile)
                .flatMap(Optional::stream)
                .findFirst();
    }

    private static Optional<String> rewriteJacocoDestfile(String argument, String workerId) {
        Optional<Path> execFile = jacocoWorkerExecFile(argument, workerId);
        if (execFile.isEmpty()) {
            return Optional.empty();
        }
        int valueStart = argument.indexOf("destfile=") + "destfile=".length();
        int valueEnd = argument.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = argument.length();
        }
        return Optional.of(argument.substring(0, valueStart) + execFile.orElseThrow() + argument.substring(valueEnd));
    }

    private static Optional<Path> jacocoWorkerExecFile(String argument, String workerId) {
        Optional<Path> canonicalExecFile = jacocoExecFile(argument);
        if (canonicalExecFile.isEmpty()) {
            return Optional.empty();
        }
        Path execFile = canonicalExecFile.orElseThrow();
        Path parent = execFile.getParent();
        if (parent == null) {
            return Optional.empty();
        }
        Path workerExecFile = parent.resolve("workers").resolve(workerId).resolve(execFile.getFileName())
                .toAbsolutePath()
                .normalize();
        try {
            Files.createDirectories(workerExecFile.getParent());
        } catch (IOException exception) {
            throw new TestRunException("Could not create worker coverage directory " + workerExecFile.getParent() + ".", exception);
        }
        return Optional.of(workerExecFile);
    }

    private static Optional<Path> jacocoExecFile(String argument) {
        if (argument == null || !argument.startsWith("-javaagent:")
                || !argument.toLowerCase(Locale.ROOT).contains("jacoco")) {
            return Optional.empty();
        }
        int valueStart = argument.indexOf("destfile=");
        if (valueStart < 0) {
            return Optional.empty();
        }
        valueStart += "destfile=".length();
        int valueEnd = argument.indexOf(',', valueStart);
        if (valueEnd < 0) {
            valueEnd = argument.length();
        }
        return Optional.of(Path.of(argument.substring(valueStart, valueEnd)));
    }

    static PlainJunitWorkerRunResult runPlainJunitWorker(
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

    private static List<Path> absolutePaths(List<Path> classpath) {
        return classpath.stream()
                .map(path -> path.toAbsolutePath().normalize())
                .toList();
    }

    private record WorkerPoolRunResult(
            String output,
            int workerRequests,
            long startupNanos,
            long requestNanos) {
    }

    private record WorkerTaskResult(
            String className,
            PlainJunitWorkerRunResult result) {
    }
}
