package com.zolt.build;

import static com.zolt.build.TestRunServiceTestSupport.config;
import static com.zolt.build.TestRunServiceTestSupport.configWithTestRuntime;
import static com.zolt.build.TestRunServiceTestSupport.enabledFrameworkTestRunner;
import static com.zolt.build.TestRunServiceTestSupport.quarkusConfig;
import static com.zolt.build.TestRunServiceTestSupport.service;
import static com.zolt.build.TestRunServiceTestSupport.source;
import static com.zolt.build.TestRunServiceLockfileTestSupport.writeConsoleAndJbossLogManagerLockfile;
import static com.zolt.build.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkDetector;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.build.junit.PlainJunitWorkerRunResult;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.TestSuiteSettings;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRunServiceFrameworkRunnerTest {
    @TempDir
    private Path projectDir;

    @Test
    void optInPlainJUnitWorkerRunsInsteadOfConsoleLauncher() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> javaCommands = new ArrayList<>();
        List<List<Path>> workerClasspaths = new ArrayList<>();
        List<List<Path>> testRuntimeClasspaths = new ArrayList<>();
        List<Path> testOutputDirectories = new ArrayList<>();
        List<TestSelection> selections = new ArrayList<>();
        List<TestJvmArguments> jvmArguments = new ArrayList<>();
        List<Map<String, String>> environments = new ArrayList<>();
        List<Optional<Path>> reportDirectories = new ArrayList<>();
        List<List<String>> events = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> {
                    javaCommands.add(command);
                    return new JavaRunner.ProcessResult(0, "direct java should not run\n");
                },
                new JdkDetector(),
                FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, testJvmArguments, environment, reportsDirectory, testEvents) -> {
                    workerClasspaths.add(workerClasspath);
                    testRuntimeClasspaths.add(testRuntimeClasspath);
                    testOutputDirectories.add(testOutputDirectory);
                    selections.add(testSelection);
                    jvmArguments.add(testJvmArguments);
                    environments.add(environment);
                    reportDirectories.add(reportsDirectory);
                    events.add(testEvents);
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("worker tests passed\n", 0),
                            12_000_000L,
                            34_000_000L);
                },
                true);

        TestRunResult result = service.runTests(
                projectDir,
                configWithTestRuntime(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                new TestJvmArguments(List.of("-Dlibrary.mode=true")),
                TestReportSettings.reportsDirectory(Path.of("target/test-reports")),
                List.of("skipped"));

        assertEquals("worker tests passed\n", result.output());
        assertEquals("zolt-junit-worker", result.testRunner());
        assertTrue(javaCommands.isEmpty());
        assertEquals(List.of(Path.of("/zolt/zolt.jar")), workerClasspaths.getFirst());
        assertEquals(projectDir.resolve("target/test-classes").toAbsolutePath().normalize(), testOutputDirectories.getFirst());
        assertTrue(selections.getFirst().emptySelection());
        assertEquals(List.of(
                "-Dconfigured=true",
                "-Dlogs.dir=" + projectDir.toAbsolutePath().normalize().resolve("test-logs"),
                "-Dlibrary.mode=true"), jvmArguments.getFirst().values());
        assertEquals(Map.of(
                "APP_HOME", projectDir.toAbsolutePath().normalize().toString(),
                "TZ", "America/Chicago"), environments.getFirst());
        assertEquals(
                Optional.of(projectDir.resolve("target/test-reports").toAbsolutePath().normalize()),
                reportDirectories.getFirst());
        assertEquals(List.of("failed", "skipped"), events.getFirst());
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path -> path.toString().contains("target/test-classes")));
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path -> path.toString().contains("target/classes")));
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path ->
                path.toString().contains("junit-platform-console-standalone-1.11.4.jar")));
        assertEquals(3, result.testRuntimeClasspathEntries());
        assertEquals(4, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
        assertEquals(12_000_000L, result.testRunnerStartupNanos());
        assertEquals(34_000_000L, result.testRunnerRequestNanos());
    }

    @Test
    void optInPlainJUnitWorkerFailureProducesActionableError() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                new JdkDetector(),
                FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents) ->
                        new PlainJunitWorkerRunResult(
                                new JunitWorkerClient.WorkerRunResult("assertion failed\n", 1),
                                12_000_000L,
                                34_000_000L),
                true);

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("JUnit worker tests failed with exit code 1"));
        assertTrue(exception.getMessage().contains("Fix failing tests"));
        assertTrue(exception.getMessage().contains("assertion failed"));
    }

    @Test
    void parallelSafeSuiteRunsThroughBoundedWorkerPool() throws Exception {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/DbOneTest.java", "package com.example; public final class DbOneTest {}\n");
        source(projectDir, "src/test/java/com/example/DbTwoTest.java", "package com.example; public final class DbTwoTest {}\n");
        source(projectDir, "src/test/java/com/example/KafkaTest.java", "package com.example; public final class KafkaTest {}\n");
        source(projectDir, "src/test/java/com/example/NoLockTest.java", "package com.example; public final class NoLockTest {}\n");
        List<TestSelection> selections = Collections.synchronizedList(new ArrayList<>());
        List<TestJvmArguments> workerJvmArguments = Collections.synchronizedList(new ArrayList<>());
        List<Map<String, String>> environments = Collections.synchronizedList(new ArrayList<>());
        List<Optional<Path>> reportDirectories = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger activeDatabaseWorkers = new AtomicInteger();
        AtomicBoolean lockViolation = new AtomicBoolean();
        CountDownLatch databaseWorkerStarted = new CountDownLatch(1);
        CountDownLatch releaseDatabaseWorker = new CountDownLatch(1);
        Thread databaseReleaseThread = new Thread(() -> {
            awaitLatch(databaseWorkerStarted);
            releaseDatabaseWorker.countDown();
        });
        databaseReleaseThread.start();
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                new JdkDetector(),
                FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, testJvmArguments, environment, reportsDirectory, testEvents) -> {
                    selections.add(testSelection);
                    workerJvmArguments.add(testJvmArguments);
                    environments.add(environment);
                    reportDirectories.add(reportsDirectory);
                    String className = testSelection.classSelectors().getFirst();
                    if (className.startsWith("com.example.Db")) {
                        if (activeDatabaseWorkers.incrementAndGet() > 1) {
                            lockViolation.set(true);
                        }
                        databaseWorkerStarted.countDown();
                        try {
                            awaitLatch(releaseDatabaseWorker);
                        } finally {
                            activeDatabaseWorkers.decrementAndGet();
                        }
                    }
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("passed " + className + "\n", 0),
                            10L,
                            20L);
                },
                true);

        Path coverageExec = projectDir.resolve("target/coverage/jacoco.exec").toAbsolutePath().normalize();
        TestJvmArguments coverageArguments =
                new TestJvmArguments(List.of("-javaagent:/tools/org.jacoco.agent.jar=destfile=" + coverageExec + ",append=false"));
        TestRunResult result = service.runTests(projectDir, parallelFastConfig(), projectDir.resolve("cache"),
                TestSelection.empty(), coverageArguments, TestReportSettings.reportsDirectory(Path.of("target/test-reports")),
                List.of(), "fast");

        assertEquals("zolt-junit-worker", result.testRunner());
        assertEquals(4, result.testDiscoveryScanRoots());
        assertTrue(result.output().contains("passed com.example.DbOneTest"));
        assertTrue(result.output().contains("passed com.example.DbTwoTest"));
        assertEquals(
                List.of(
                        "com.example.DbOneTest",
                        "com.example.DbTwoTest",
                        "com.example.KafkaTest",
                        "com.example.NoLockTest"),
                selections.stream()
                        .flatMap(selection -> selection.classSelectors().stream())
                        .sorted()
                        .toList());
        assertFalse(lockViolation.get());
        assertEquals(4, environments.size());
        assertEquals(4, workerJvmArguments.size());
        assertTrue(workerJvmArguments.stream()
                .map(arguments -> arguments.values().getFirst())
                .allMatch(argument -> argument.contains("target/coverage/workers/wave-")
                        && argument.endsWith("/jacoco.exec,append=false")));
        assertTrue(environments.stream().allMatch(environment -> environment.containsKey("ZOLT_TEST_WORKER_ID")));
        assertTrue(environments.stream().allMatch(environment -> environment.containsKey("ZOLT_TEST_WORKER_OUTPUT_DIR")));
        assertTrue(environments.stream().allMatch(environment -> environment.containsKey("ZOLT_COVERAGE_EXEC_FILE")));
        assertTrue(reportDirectories.stream().allMatch(Optional::isPresent));
        assertTrue(reportDirectories.stream()
                .map(Optional::orElseThrow)
                .allMatch(path -> path.toString().contains("target/test-reports/workers/wave-")));
        String reportManifest = Files.readString(projectDir.resolve("target/test-reports/workers/zolt-workers.json"));
        assertTrue(reportManifest.contains("\"wave-1-worker-1\""));
        assertTrue(reportManifest.contains("\"wave-2-worker-1\""));
        String coverageManifest = Files.readString(projectDir.resolve("target/coverage/workers/zolt-workers.json"));
        assertTrue(coverageManifest.contains("\"wave-1-worker-1\""));
        assertTrue(coverageManifest.contains("\"wave-2-worker-1\""));
    }

    @Test
    void workerPoolFailurePreservesWorkerOutput() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/FailingTest.java", "package com.example; public final class FailingTest {}\n");
        source(projectDir, "src/test/java/com/example/PassingTest.java", "package com.example; public final class PassingTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                new JdkDetector(),
                FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, testJvmArguments, environment, reportsDirectory, testEvents) -> {
                    String className = testSelection.classSelectors().getFirst();
                    if (className.equals("com.example.FailingTest")) {
                        return new PlainJunitWorkerRunResult(
                                new JunitWorkerClient.WorkerRunResult("java.lang.AssertionError: nope\n\tat com.example.FailingTest\n", 1),
                                10L,
                                20L);
                    }
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("passed " + className + "\n", 0),
                            10L,
                            20L);
                },
                true);

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(
                        projectDir,
                        parallelFastConfig(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "fast"));

        assertTrue(exception.getMessage().contains("JUnit worker tests failed with exit code 1"));
        assertTrue(exception.getMessage().contains("com.example.FailingTest"));
        assertTrue(exception.getMessage().contains("java.lang.AssertionError: nope"));
    }

    @Test
    void workerPoolCancellationInterruptsWorkers() throws Exception {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/SlowOneTest.java", "package com.example; public final class SlowOneTest {}\n");
        source(projectDir, "src/test/java/com/example/SlowTwoTest.java", "package com.example; public final class SlowTwoTest {}\n");
        CountDownLatch workerStarted = new CountDownLatch(1);
        AtomicBoolean workerInterrupted = new AtomicBoolean();
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                new JdkDetector(),
                FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, testJvmArguments, environment, reportsDirectory, testEvents) -> {
                    workerStarted.countDown();
                    try {
                        new CountDownLatch(1).await();
                    } catch (InterruptedException exception) {
                        workerInterrupted.set(true);
                        Thread.currentThread().interrupt();
                    }
                    return new PlainJunitWorkerRunResult(
                            new JunitWorkerClient.WorkerRunResult("cancelled\n", 0),
                            10L,
                            20L);
                },
                true);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        Thread runnerThread = new Thread(() -> {
            try {
                service.runTests(
                        projectDir,
                        parallelFastConfig(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "fast");
            } catch (Throwable throwable) {
                thrown.set(throwable);
            }
        });

        runnerThread.start();
        assertTrue(workerStarted.await(5, TimeUnit.SECONDS));
        runnerThread.interrupt();
        runnerThread.join(5_000L);

        assertFalse(runnerThread.isAlive());
        assertTrue(thrown.get() instanceof TestRunException);
        assertTrue(thrown.get().getMessage().contains("JUnit worker pool was interrupted"));
        assertTrue(workerInterrupted.get());
    }

    @Test
    void quarkusPlainJUnitRunsThroughQuarkusTestWorker() throws IOException {
        writeConsoleAndJbossLogManagerLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> javaCommands = new ArrayList<>();
        List<FrameworkTestRunRequest> frameworkRequests = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> {
                    javaCommands.add(command);
                    return new JavaRunner.ProcessResult(0, "direct java should not run\n");
                },
                enabledFrameworkTestRunner(request -> {
                    frameworkRequests.add(request);
                    return Optional.of(new FrameworkTestRunResult(
                            "Worker tests successful\n",
                            false,
                            1,
                            1));
                }));

        TestRunResult result = service.runTests(projectDir, quarkusConfig(), projectDir.resolve("cache"));

        assertEquals("Worker tests successful\n", result.output());
        assertEquals("quarkus-test-worker", result.testRunner());
        assertEquals(4, result.testRuntimeClasspathEntries());
        assertEquals(1, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
        assertTrue(result.testRunnerRequestNanos() >= 0L);
        assertTrue(javaCommands.isEmpty());
        FrameworkTestRunRequest request = frameworkRequests.getFirst();
        assertEquals(projectDir, request.projectDirectory());
        assertEquals(quarkusConfig(), request.config());
        assertEquals(projectDir.resolve("target/classes"), request.mainOutputDirectory());
        assertEquals(projectDir.resolve("target/test-classes"), request.testOutputDirectory());
        assertTrue(request.testRuntimeClasspath().stream()
                .anyMatch(path -> path.getFileName().toString().startsWith("junit-platform-console-standalone")));
    }

    @Test
    void quarkusWorkerFailureProducesTestRunError() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                enabledFrameworkTestRunner(request -> {
                    throw new TestRunException("worker failed");
                }));

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, quarkusConfig(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("worker failed"));
    }

    @Test
    void frameworkRunnerCanRejectReportsDirectoryBeforeRunning() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> {
                    throw new AssertionError("direct Java runner should not run for an enabled framework.");
                },
                enabledFrameworkTestRunner(
                        request -> {
                            throw new AssertionError("framework runner should reject reports before running.");
                        },
                        "framework reports are not supported yet"));

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(
                        projectDir,
                        quarkusConfig(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.reportsDirectory(Path.of("target/test-reports"))));

        assertTrue(exception.getMessage().contains("framework reports are not supported yet"));
    }

    private static ProjectConfig parallelFastConfig() {
        return config().withBuildSettings(BuildSettings.defaults().withTestSuites(Map.of(
                "fast",
                new TestSuiteSettings(
                        List.of("*Test"),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        3,
                        Map.of(
                                "com.example.DbOneTest",
                                List.of("database"),
                                "com.example.DbTwoTest",
                                List.of("database"))))));
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            assertTrue(latch.await(2, TimeUnit.SECONDS));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Interrupted while waiting for test latch.", exception);
        }
    }

}
