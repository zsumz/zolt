package sh.zolt.build.testruntime;

import static sh.zolt.build.testruntime.TestRunServiceTestSupport.commandArgumentAfter;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.config;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.configWithTestRuntime;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.service;
import static sh.zolt.build.testruntime.TestRunServiceTestSupport.source;
import static sh.zolt.build.testruntime.TestRunServiceLockfileTestSupport.createSymlink;
import static sh.zolt.build.testruntime.TestRunServiceLockfileTestSupport.writeConsoleLockfile;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.build.BuildResult;
import sh.zolt.build.testruntime.compile.TestCompileResult;
import sh.zolt.build.run.JavaRunner;
import sh.zolt.build.profile.TestProfileSettings;
import sh.zolt.classpath.Classpath;
import sh.zolt.classpath.ClasspathSet;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.TestSuiteSettings;
import sh.zolt.test.TestProfileHistory;
import sh.zolt.test.runtime.TestJvmArguments;
import sh.zolt.test.runtime.TestRunException;
import sh.zolt.test.TestSelection;
import sh.zolt.test.shard.TestShardSpec;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRunServiceRuntimeOptionsTest {
    @TempDir
    private Path projectDir;

    @Test
    void passesReportsDirectoryToJUnitConsole() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.reportsDirectory(Path.of("target/test-reports")));

        Path reportsDirectory = projectDir.resolve("target/test-reports").toAbsolutePath().normalize();
        assertEquals(Optional.of(reportsDirectory), result.reportsDirectory());
        List<String> command = commands.getFirst();
        assertEquals(reportsDirectory.toString(), commandArgumentAfter(command, "--reports-dir"));
    }

    @Test
    void reportsDirectoryRejectsExistingSymlinkThatEscapesProject() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-test-reports-");
        createSymlink(projectDir.resolve("target/test-reports"), outside);
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(
                        projectDir,
                        config(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.reportsDirectory(Path.of("target/test-reports"))));

        assertTrue(exception.getMessage().contains("--reports-dir"));
        assertTrue(exception.getMessage().contains("target/test-reports"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void reportsDirectoryFailurePointsToReports() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) ->
                new JavaRunner.ProcessResult(1, "test failure\n"));

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(
                        projectDir,
                        config(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.reportsDirectory(Path.of("target/test-reports"))));

        assertTrue(exception.getMessage().contains("test failure"));
        assertTrue(exception.getMessage().contains("Test reports: "
                + projectDir.resolve("target/test-reports").toAbsolutePath().normalize()));
    }

    @Test
    void failsBeforeLaunchingTestsWhenCachedTestJarDoesNotMatchLockfileHash() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path jar = cacheRoot.resolve(
                "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar");
        Files.createDirectories(jar.getParent());
        Files.writeString(jar, "corrupted console jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.junit.platform:junit-platform-console-standalone"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) -> {
            throw new AssertionError("test JVM should not be launched with a corrupted cached jar");
        });

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> service.runTests(projectDir, config(), cacheRoot));

        assertTrue(exception.getMessage().contains(
                "Cached jar integrity check failed for org.junit.platform:junit-platform-console-standalone:1.11.4"));
    }

    @Test
    void appliesConfiguredTestRuntimeToJUnitConsole() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        List<Map<String, String>> environments = new ArrayList<>();
        JavaRunner.ProcessRunner processRunner = new JavaRunner.ProcessRunner() {
            @Override
            public JavaRunner.ProcessResult run(List<String> command, java.util.function.Consumer<String> outputConsumer) {
                throw new AssertionError("Environment-aware Java runner should be used for tests.");
            }

            @Override
            public JavaRunner.ProcessResult run(
                    List<String> command,
                    Map<String, String> environment,
                    java.util.function.Consumer<String> outputConsumer) {
                commands.add(command);
                environments.add(environment);
                return new JavaRunner.ProcessResult(0, "Tests successful\n");
            }
        };
        TestRunService service = service(processRunner);

        TestRunResult result = service.runTests(
                projectDir,
                configWithTestRuntime(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                new TestJvmArguments(List.of("-Dcli=true")));

        Path root = projectDir.toAbsolutePath().normalize();
        assertEquals(List.of(
                "-Dconfigured=true",
                "-Dlogs.dir=" + root.resolve("test-logs"),
                "-Dcli=true"), result.testJvmArguments().values());
        List<String> command = commands.getFirst();
        assertEquals("-Dconfigured=true", command.get(1));
        assertEquals("-Dlogs.dir=" + root.resolve("test-logs"), command.get(2));
        assertEquals("-Dcli=true", command.get(3));
        assertEquals("-Duser.dir=" + root, command.get(4));
        assertEquals(Map.of(
                "APP_HOME", root.toString(),
                "TZ", "America/Chicago"), environments.getFirst());
        assertEquals("tree", commandArgumentAfter(command, "--details"));
        assertEquals("ascii", commandArgumentAfter(command, "--details-theme"));
    }

    @Test
    void cliTestEventsEnableDetailedJUnitConsoleOutput() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        service.runTests(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.disabled(),
                List.of("failed", "passed", "failed"));

        List<String> command = commands.getFirst();
        assertEquals("tree", commandArgumentAfter(command, "--details"));
        assertEquals("ascii", commandArgumentAfter(command, "--details-theme"));
    }

    @Test
    void profileSettingsRoutePlainJUnitThroughWorkerWithProfileDirectory() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<Optional<Path>> profileDirectories = new ArrayList<>();
        List<Map<String, String>> environments = new ArrayList<>();
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> {
                    commands.add(command);
                    return new JavaRunner.ProcessResult(0, "Console should not run\n");
                },
                new TestRunServiceTestSupport.CachingJdkChecker(),
                sh.zolt.framework.FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    profileDirectories.add(profileDirectory);
                    environments.add(environment);
                    return new sh.zolt.build.junit.PlainJunitWorkerRunResult(
                            new sh.zolt.junit.JunitWorkerClient.WorkerRunResult("Tests found: 1\nTests succeeded: 1\n", 0),
                            10L,
                            20L);
                },
                false);

        TestRunResult result = service.runTests(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.disabled(),
                List.of(),
                "all",
                null,
                TestProfileSettings.fromCli(true, Path.of("target/test-profile")));

        Path profileDirectory = projectDir.resolve("target/test-profile").toAbsolutePath().normalize();
        assertEquals("zolt-junit-worker", result.testRunner());
        assertEquals(Optional.of(profileDirectory), result.profileDirectory());
        assertEquals(List.of(Optional.of(profileDirectory)), profileDirectories);
        Map<String, String> environment = environments.getFirst();
        assertEquals(projectDir.toAbsolutePath().normalize().toString(), environment.get("ZOLT_TEST_PROFILE_PROJECT_ROOT"));
        assertEquals("demo", environment.get("ZOLT_TEST_PROFILE_PROJECT"));
        assertEquals("all", environment.get("ZOLT_TEST_PROFILE_SUITE"));
        assertEquals("", environment.get("ZOLT_TEST_PROFILE_SHARD"));
        assertEquals("", environment.get("ZOLT_TEST_PROFILE_MEMBER"));
        assertTrue(commands.isEmpty());
    }

    @Test
    void profileSettingsUseShardSpecificProfileDirectory() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<Optional<Path>> profileDirectories = new ArrayList<>();
        List<Map<String, String>> environments = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "Console should not run\n"),
                new TestRunServiceTestSupport.CachingJdkChecker(),
                sh.zolt.framework.FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    profileDirectories.add(profileDirectory);
                    environments.add(environment);
                    return new sh.zolt.build.junit.PlainJunitWorkerRunResult(
                            new sh.zolt.junit.JunitWorkerClient.WorkerRunResult("Tests found: 1\nTests succeeded: 1\n", 0),
                            10L,
                            20L);
                },
                false);
        TestRunResult result = service.runTests(
                projectDir,
                config(),
                projectDir.resolve("cache"),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.disabled(),
                List.of(),
                "all",
                new sh.zolt.test.shard.TestShardSpec(1, 2),
                TestProfileSettings.fromCli(true, Path.of("target/test-profile")));

        Path profileDirectory = projectDir.resolve("target/test-profile/shards/all/shard-1-of-2").toAbsolutePath().normalize();
        assertEquals(Optional.of(profileDirectory), result.profileDirectory());
        assertEquals(List.of(Optional.of(profileDirectory)), profileDirectories);
        Map<String, String> environment = environments.getFirst();
        assertEquals("demo", environment.get("ZOLT_TEST_PROFILE_PROJECT"));
        assertEquals("all", environment.get("ZOLT_TEST_PROFILE_SUITE"));
        assertEquals("1/2", environment.get("ZOLT_TEST_PROFILE_SHARD"));
        assertEquals("", environment.get("ZOLT_TEST_PROFILE_MEMBER"));
    }

    @Test
    void failedProfiledWorkerRunIncludesSlowSummaryWhenProfileExists() throws IOException {
        writeConsoleLockfile(projectDir);
        source(projectDir, "src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source(projectDir, "src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "Console should not run\n"),
                new TestRunServiceTestSupport.CachingJdkChecker(),
                sh.zolt.framework.FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    Path profile = profileDirectory.orElseThrow();
                    writeProfile(profile, """
                            {
                              "schemaVersion": 1,
                              "tests": [
                                {
                                  "uniqueId": "slow",
                                  "className": "com.example.SlowTest",
                                  "methodName": "failsSlowly",
                                  "displayName": "failsSlowly()",
                                  "durationMillis": 600,
                                  "workerId": ""
                                }
                              ],
                              "containers": [
                                {
                                  "uniqueId": "slow-class",
                                  "className": "com.example.SlowTest",
                                  "methodName": "",
                                  "displayName": "SlowTest",
                                  "durationMillis": 700,
                                  "workerId": "",
                                  "testCount": 1
                                }
                              ]
                            }
                            """);
                    return new sh.zolt.build.junit.PlainJunitWorkerRunResult(
                            new sh.zolt.junit.JunitWorkerClient.WorkerRunResult("Tests failed: 1\n", 1),
                            10L,
                            20L);
                },
                false);

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(
                        projectDir,
                        config(),
                        projectDir.resolve("cache"),
                        TestSelection.empty(),
                        TestJvmArguments.empty(),
                        TestReportSettings.disabled(),
                        List.of(),
                        "all",
                        null,
                        TestProfileSettings.fromCli(true, Path.of("target/test-profile"), 5, "100ms")));

        assertTrue(exception.getMessage().contains("Tests failed: 1"));
        assertTrue(exception.getMessage().contains("Slowest tests:"));
        assertTrue(exception.getMessage().contains("600 ms com.example.SlowTest#failsSlowly"));
        assertTrue(exception.getMessage().contains("Slowest classes:"));
        assertTrue(exception.getMessage().contains("700 ms com.example.SlowTest (1 test)"));
    }

    @Test
    void workerPoolProfilesAreMergedAndReadableAsHistory() throws IOException {
        Path testOutput = projectDir.resolve("target/test-classes");
        writeClassFile(testOutput, "com/example/AlphaTest.class");
        writeClassFile(testOutput, "com/example/BetaTest.class");
        writeClassFile(testOutput, "com/example/DeltaTest.class");
        writeClassFile(testOutput, "com/example/GammaTest.class");
        Path mainOutput = projectDir.resolve("target/classes");
        Files.createDirectories(mainOutput);
        List<Map<String, String>> environments = Collections.synchronizedList(new ArrayList<>());
        TestRunService service = service(
                (command, outputConsumer) -> {
                    throw new AssertionError("Console should not run for pooled profiled workers.");
                },
                new TestRunServiceTestSupport.CachingJdkChecker(),
                sh.zolt.framework.FrameworkTestRunner.none(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory, testSelection, jvmArguments, environment, reportsDirectory, testEvents, profileDirectory) -> {
                    environments.add(environment);
                    String className = testSelection.classSelectors().getFirst();
                    writeProfile(
                            profileDirectory.orElseThrow(),
                            profileJson(environment.get("ZOLT_TEST_WORKER_ID"), className, durationFor(className), environment));
                    return new sh.zolt.build.junit.PlainJunitWorkerRunResult(
                            new sh.zolt.junit.JunitWorkerClient.WorkerRunResult("passed " + className + "\n", 0),
                            10L,
                            20L);
                },
                true);
        ProjectConfig profiledSuiteConfig = config().withBuildSettings(BuildSettings.defaults().withTestSuites(Map.of(
                "fast",
                new TestSuiteSettings(
                        List.of("*Test"),
                        List.of(),
                        List.of(),
                        List.of(),
                        true,
                        2,
                        Map.of()))));
        TestRunResult result = service.runCompiledTests(
                projectDir,
                profiledSuiteConfig,
                classpathSetWithConsoleJar(),
                new TestCompileResult(
                        new BuildResult(Optional.empty(), 0, 0, mainOutput, ""),
                        4,
                        0,
                        testOutput,
                        ""),
                TestSelection.empty(),
                TestJvmArguments.empty(),
                TestReportSettings.disabled(),
                List.of(),
                "fast",
                new TestShardSpec(1, 2),
                TestProfileSettings.fromCli(true, Path.of("target/test-profile"), 5, "100ms")
                        .forWorkspaceMember("modules/zolt-test-runtime"));

        Path profileDirectory = projectDir.resolve(
                        "target/test-profile/modules/zolt-test-runtime/shards/fast/shard-1-of-2")
                .toAbsolutePath()
                .normalize();
        assertEquals("zolt-junit-worker", result.testRunner());
        assertEquals(2, result.testDiscoveryScanRoots());
        assertEquals(Optional.of(profileDirectory), result.profileDirectory());
        assertTrue(environments.stream().allMatch(environment ->
                environment.get("ZOLT_TEST_PROFILE_PROJECT_ROOT").equals(projectDir.toAbsolutePath().normalize().toString())));
        assertTrue(environments.stream().allMatch(environment -> environment.get("ZOLT_TEST_PROFILE_PROJECT").equals("demo")));
        assertTrue(environments.stream().allMatch(environment -> environment.get("ZOLT_TEST_PROFILE_MEMBER").equals("modules/zolt-test-runtime")));
        assertTrue(environments.stream().allMatch(environment -> environment.get("ZOLT_TEST_PROFILE_SUITE").equals("fast")));
        assertTrue(environments.stream().allMatch(environment -> environment.get("ZOLT_TEST_PROFILE_SHARD").equals("1/2")));

        String shardManifest = Files.readString(projectDir.resolve("target/test-shards/fast/shard-1-of-2.json"));
        assertTrue(shardManifest.contains("\"selectedEntries\": 2"));
        assertTrue(shardManifest.contains("\"com.example.AlphaTest\""));
        assertTrue(shardManifest.contains("\"com.example.DeltaTest\""));

        String mergedProfile = Files.readString(profileDirectory.resolve("profile.json"));
        assertTrue(mergedProfile.contains("\"member\": \"modules/zolt-test-runtime\""));
        assertTrue(mergedProfile.contains("\"suite\": \"fast\""));
        assertTrue(mergedProfile.contains("\"shard\": \"1/2\""));
        assertTrue(mergedProfile.contains("\"testsFound\": 2"));
        assertTrue(mergedProfile.contains("\"com.example.AlphaTest\""));
        assertTrue(mergedProfile.contains("\"com.example.DeltaTest\""));
        TestProfileHistory history = TestProfileHistory.read(projectDir, profileDirectory.resolve("profile.json"));
        assertEquals(Optional.of(profileDirectory.resolve("profile.json")), history.source());
        assertEquals(
                Map.of(
                        "com.example.AlphaTest", 101L,
                        "com.example.DeltaTest", 303L),
                history.classDurations());
        assertEquals(List.of(), history.diagnostics());
    }

    private static void writeProfile(Path profileDirectory, String profileJson) {
        try {
            Files.createDirectories(profileDirectory);
            Files.writeString(profileDirectory.resolve("profile.json"), profileJson);
        } catch (IOException exception) {
            throw new AssertionError("could not write test profile fixture", exception);
        }
    }

    private static void writeClassFile(Path testOutput, String relativePath) throws IOException {
        Path classFile = testOutput.resolve(relativePath);
        Files.createDirectories(classFile.getParent());
        Files.write(classFile, new byte[] {0});
    }

    private ClasspathSet classpathSetWithConsoleJar() {
        Classpath empty = new Classpath(List.of());
        return new ClasspathSet(
                empty,
                empty,
                new Classpath(List.of(projectDir.resolve(
                        "cache/org/junit/platform/junit-platform-console-standalone/1.11.4/junit-platform-console-standalone-1.11.4.jar"))),
                empty,
                empty,
                empty);
    }

    private static long durationFor(String className) {
        return switch (className) {
            case "com.example.AlphaTest" -> 101L;
            case "com.example.DeltaTest" -> 303L;
            default -> throw new AssertionError("unexpected class " + className);
        };
    }

    private static String profileJson(
            String workerId,
            String className,
            long durationMillis,
            Map<String, String> environment) {
        return """
                {
                  "schemaVersion": 1,
                  "runner": "zolt-junit-worker",
                  "workerId": "%s",
                  "projectRoot": "%s",
                  "project": "%s",
                  "member": "%s",
                  "suite": "%s",
                  "shard": "%s",
                  "summary": {
                    "testsFound": 1,
                    "testsSucceeded": 1,
                    "testsFailed": 0,
                    "testsSkipped": 0,
                    "testsAborted": 0,
                    "durationMillis": %d
                  },
                  "tests": [
                    {
                      "uniqueId": "%s#runs",
                      "className": "%s",
                      "methodName": "runs",
                      "displayName": "runs()",
                      "durationMillis": %d,
                      "workerId": "%s"
                    }
                  ],
                  "containers": [
                    {
                      "uniqueId": "%s",
                      "className": "%s",
                      "methodName": "",
                      "displayName": "%s",
                      "durationMillis": %d,
                      "workerId": "%s",
                      "testCount": 1
                    }
                  ]
                }
                """.formatted(
                workerId,
                environment.get("ZOLT_TEST_PROFILE_PROJECT_ROOT"),
                environment.get("ZOLT_TEST_PROFILE_PROJECT"),
                environment.get("ZOLT_TEST_PROFILE_MEMBER"),
                environment.get("ZOLT_TEST_PROFILE_SUITE"),
                environment.get("ZOLT_TEST_PROFILE_SHARD"),
                durationMillis,
                className,
                className,
                durationMillis,
                workerId,
                className,
                className,
                className.substring(className.lastIndexOf('.') + 1),
                durationMillis,
                workerId);
    }
}
