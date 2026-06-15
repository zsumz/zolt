package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.framework.FrameworkTestRunRequest;
import com.zolt.framework.FrameworkTestRunResult;
import com.zolt.framework.FrameworkTestRunner;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.project.TestRuntimeSettings;
import com.zolt.test.TestSelection;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRunServiceFrameworkRunnerTest {
    @TempDir
    private Path projectDir;

    @Test
    void optInPlainJUnitWorkerRunsInsteadOfConsoleLauncher() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
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
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
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
        writeConsoleAndJbossLogManagerLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
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
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
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
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
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

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            FrameworkTestRunner frameworkTestRunner) {
        return service(
                processRunner,
                new JdkDetector(),
                frameworkTestRunner);
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            FrameworkTestRunner frameworkTestRunner) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                frameworkTestRunner,
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents) -> {
                    throw new AssertionError("Plain JUnit worker should not run for this test.");
                },
                false,
                ":");
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            FrameworkTestRunner frameworkTestRunner,
            java.util.function.Supplier<List<Path>> plainJunitWorkerClasspath,
            TestRunService.PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                frameworkTestRunner,
                plainJunitWorkerClasspath,
                plainJunitWorkerRunner,
                plainJunitWorkerEnabled,
                ":");
    }

    private static FrameworkTestRunner enabledFrameworkTestRunner(
            Function<FrameworkTestRunRequest, Optional<FrameworkTestRunResult>> runner) {
        return enabledFrameworkTestRunner(runner, null);
    }

    private static FrameworkTestRunner enabledFrameworkTestRunner(
            Function<FrameworkTestRunRequest, Optional<FrameworkTestRunResult>> runner,
            String unsupportedReportsMessage) {
        return new FrameworkTestRunner() {
            @Override
            public Optional<FrameworkTestRunResult> runIfEnabled(FrameworkTestRunRequest request) {
                return runner.apply(request);
            }

            @Override
            public boolean isEnabled(ProjectConfig config) {
                return true;
            }

            @Override
            public String testRunnerName() {
                return "quarkus-test-worker";
            }

            @Override
            public Optional<String> unsupportedReportsMessage() {
                return Optional.ofNullable(unsupportedReportsMessage);
            }
        };
    }

    private void writeConsoleLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []
                """);
    }

    private void writeConsoleAndJbossLogManagerLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.jboss.logmanager:jboss-logmanager"
                version = "3.1.2.Final"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/jboss/logmanager/jboss-logmanager/3.1.2.Final/jboss-logmanager-3.1.2.Final.jar"
                dependencies = []
                """);
    }

    private void source(String path, String content) throws IOException {
        Path source = projectDir.resolve(path);
        Files.createDirectories(source.getParent());
        Files.writeString(source, content);
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.platform:junit-platform-console-standalone", "1.11.4"),
                BuildSettings.defaults());
    }

    private static ProjectConfig configWithTestRuntime() {
        return config().withBuildSettings(BuildSettings.defaults().withTestRuntime(new TestRuntimeSettings(
                List.of("-Dconfigured=true"),
                Map.of("logs.dir", "${project.root}/test-logs"),
                Map.of("TZ", "America/Chicago", "APP_HOME", "${project.root}"),
                List.of("failed"))));
    }

    private static ProjectConfig quarkusConfig() {
        return config().withFrameworkSettings(new FrameworkSettings(
                new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
