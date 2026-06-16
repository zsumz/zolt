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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkDetector;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
                    return new TestRunService.PlainJunitWorkerRunResult(
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
                        new TestRunService.PlainJunitWorkerRunResult(
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

}
