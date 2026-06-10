package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkChecker;
import com.zolt.doctor.JdkDetector;
import com.zolt.doctor.JdkStatus;
import com.zolt.junit.JunitWorkerClient;
import com.zolt.project.BuildSettings;
import com.zolt.project.FrameworkSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import com.zolt.project.QuarkusPackageMode;
import com.zolt.project.QuarkusSettings;
import com.zolt.quarkus.QuarkusAugmentationException;
import com.zolt.quarkus.QuarkusTestRunnerDescriptor;
import com.zolt.quarkus.QuarkusTestRunnerDescriptorWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestRunServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void runsJUnitConsoleWithTestRuntimeClasspath() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("Tests successful\n", result.output());
        assertEquals("junit-console", result.testRunner());
        assertEquals(3, result.testRuntimeClasspathEntries());
        assertEquals(1, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
        List<String> command = commands.getFirst();
        String userDirProperty = "-Duser.dir=" + projectDir.toAbsolutePath().normalize();
        assertEquals(userDirProperty, command.get(1));
        assertTrue(command.indexOf(userDirProperty) < command.indexOf("-classpath"));
        assertTrue(command.contains("org.junit.platform.console.ConsoleLauncher"));
        assertTrue(command.contains("execute"));
        assertTrue(command.contains("--disable-banner"));
        assertTrue(command.contains("--scan-class-path="
                + projectDir.resolve("target/test-classes").toAbsolutePath().normalize()));
        assertTrue(command.contains("--details"));
        String launcherClasspath = launcherClasspath(command);
        assertTrue(launcherClasspath.contains("junit-platform-console-standalone-1.11.4.jar"));
        assertFalse(launcherClasspath.contains("target/test-classes"));
        assertFalse(launcherClasspath.contains("target/classes"));
        assertTrue(command.stream().anyMatch(value -> value.contains("target/test-classes")));
        assertTrue(command.stream().anyMatch(value -> value.contains("target/classes")));
        assertTrue(command.stream().anyMatch(value -> value.contains("junit-platform-console-standalone-1.11.4.jar")));
    }

    @Test
    void compilesAdditionalJavaTestRootsBeforeRunningJUnitConsole() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", """
                package com.example;

                public final class Main {
                    public static String message() {
                        return "hello";
                    }
                }
                """);
        source("src/test/java/com/example/MainTest.java", """
                package com.example;

                public final class MainTest {
                    public String message() {
                        return Main.message();
                    }
                }
                """);
        source("src/integration-test/java/com/example/MainIT.java", """
                package com.example;

                public final class MainIT {
                    public String message() {
                        return Main.message();
                    }
                }
                """);
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(projectDir, multiRootConfig(), projectDir.resolve("cache"));

        assertEquals(2, result.compileResult().sourceCount());
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainTest.class")));
        assertTrue(Files.exists(projectDir.resolve("target/test-classes/com/example/MainIT.class")));
        List<String> command = commands.getFirst();
        assertTrue(command.contains("org.junit.platform.console.ConsoleLauncher"));
        assertTrue(command.contains("--scan-class-path="
                + projectDir.resolve("target/test-classes").toAbsolutePath().normalize()));
        assertTrue(command.stream().anyMatch(value -> value.contains("target/test-classes")));
        assertTrue(command.stream().anyMatch(value -> value.contains("junit-platform-console-standalone-1.11.4.jar")));
    }

    @Test
    void configuresJbossLogManagerWhenPresentOnTestClasspath() throws IOException {
        writeConsoleAndJbossLogManagerLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        service.runTests(projectDir, config(), projectDir.resolve("cache"));

        List<String> command = commands.getFirst();
        assertEquals("-Duser.dir=" + projectDir.toAbsolutePath().normalize(), command.get(1));
        assertEquals("-Djava.util.logging.manager=org.jboss.logmanager.LogManager", command.get(2));
        assertTrue(command.indexOf("-Djava.util.logging.manager=org.jboss.logmanager.LogManager")
                < command.indexOf("-classpath"));
        String launcherClasspath = launcherClasspath(command);
        assertTrue(launcherClasspath.contains("junit-platform-console-standalone-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("jboss-logmanager-3.1.2.Final.jar"));
        assertTrue(command.stream().anyMatch(value -> value.contains("jboss-logmanager-3.1.2.Final.jar")));
    }

    @Test
    void nonStandaloneConsoleLaunchesWithJUnitPlatformRuntimeClasspath() throws IOException {
        writeNonStandaloneConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> commands = new ArrayList<>();
        TestRunService service = service((command, outputConsumer) -> {
            commands.add(command);
            return new JavaRunner.ProcessResult(0, "Tests successful\n");
        });

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"));

        String launcherClasspath = launcherClasspath(commands.getFirst());
        assertEquals("junit-console", result.testRunner());
        assertEquals(10, result.testRuntimeClasspathEntries());
        assertEquals(7, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
        assertTrue(launcherClasspath.contains("junit-platform-console-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("junit-platform-reporting-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("junit-platform-launcher-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("junit-platform-engine-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("junit-platform-commons-1.11.4.jar"));
        assertTrue(launcherClasspath.contains("apiguardian-api-1.1.2.jar"));
        assertTrue(launcherClasspath.contains("opentest4j-1.3.0.jar"));
        assertFalse(launcherClasspath.contains("junit-jupiter-engine-5.11.4.jar"));
        assertFalse(launcherClasspath.contains("target/test-classes"));
        assertTrue(commandArgumentAfter(commands.getFirst(), "--class-path").contains("junit-jupiter-engine-5.11.4.jar"));
        assertTrue(commandArgumentAfter(commands.getFirst(), "--class-path").contains("target/test-classes"));
    }

    @Test
    void sharesCachedJdkDetectionAcrossBuildCompileAndExecution() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        CachingJdkChecker jdkChecker = new CachingJdkChecker();
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "Tests successful\n"),
                jdkChecker);

        service.runTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals(3, jdkChecker.detectCalls());
        assertEquals(1, jdkChecker.toolchainReads());
    }

    @Test
    void missingConsoleJarProducesActionableError() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) -> new JavaRunner.ProcessResult(0, ""));

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("JUnit Platform Console is not present"));
        assertTrue(exception.getMessage().contains("Run `zolt resolve`"));
        assertTrue(exception.getMessage().contains("test engines declared in [test.dependencies]"));
    }

    @Test
    void failingTestsReturnNonZeroThroughJavaRunner() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) -> new JavaRunner.ProcessResult(2, "test failed\n"));

        JavaRunException exception = assertThrows(
                JavaRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("java exited with code 2"));
        assertTrue(exception.getMessage().contains("test failed"));
    }

    @Test
    void optInPlainJUnitWorkerRunsInsteadOfConsoleLauncher() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        List<List<String>> javaCommands = new ArrayList<>();
        List<List<Path>> workerClasspaths = new ArrayList<>();
        List<List<Path>> testRuntimeClasspaths = new ArrayList<>();
        List<Path> testOutputDirectories = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> {
                    javaCommands.add(command);
                    return new JavaRunner.ProcessResult(0, "direct java should not run\n");
                },
                new JdkDetector(),
                (projectDirectory, config) -> Optional.empty(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("Quarkus test worker should not run.");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory) -> {
                    workerClasspaths.add(workerClasspath);
                    testRuntimeClasspaths.add(testRuntimeClasspath);
                    testOutputDirectories.add(testOutputDirectory);
                    return new JunitWorkerClient.WorkerRunResult("worker tests passed\n", 0);
                },
                true);

        TestRunResult result = service.runTests(projectDir, config(), projectDir.resolve("cache"));

        assertEquals("worker tests passed\n", result.output());
        assertEquals("zolt-junit-worker", result.testRunner());
        assertTrue(javaCommands.isEmpty());
        assertEquals(List.of(Path.of("/zolt/zolt.jar")), workerClasspaths.getFirst());
        assertEquals(projectDir.resolve("target/test-classes").toAbsolutePath().normalize(), testOutputDirectories.getFirst());
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path -> path.toString().contains("target/test-classes")));
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path -> path.toString().contains("target/classes")));
        assertTrue(testRuntimeClasspaths.getFirst().stream().anyMatch(path ->
                path.toString().contains("junit-platform-console-standalone-1.11.4.jar")));
        assertEquals(3, result.testRuntimeClasspathEntries());
        assertEquals(4, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
    }

    @Test
    void optInPlainJUnitWorkerFailureProducesActionableError() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                new JdkDetector(),
                (projectDirectory, config) -> Optional.empty(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("Quarkus test worker should not run.");
                },
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory) ->
                        new JunitWorkerClient.WorkerRunResult("assertion failed\n", 1),
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
        List<QuarkusTestRunnerDescriptor> descriptors = new ArrayList<>();
        List<List<Path>> workerClasspaths = new ArrayList<>();
        TestRunService service = service(
                (command, outputConsumer) -> {
                    javaCommands.add(command);
                    return new JavaRunner.ProcessResult(0, "direct java should not run\n");
                },
                (projectDirectory, config) -> Optional.of(projectDirectory
                        .resolve("target/quarkus/test-application-model.dat")
                        .toAbsolutePath()
                        .normalize()),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    workerClasspaths.add(workerClasspath);
                    descriptors.add(descriptor);
                    return "Worker tests successful\n";
                });

        TestRunResult result = service.runTests(projectDir, quarkusConfig(), projectDir.resolve("cache"));

        assertEquals("Worker tests successful\n", result.output());
        assertEquals("quarkus-test-worker", result.testRunner());
        assertEquals(4, result.testRuntimeClasspathEntries());
        assertEquals(1, result.testLauncherClasspathEntries());
        assertEquals(1, result.testDiscoveryScanRoots());
        assertTrue(javaCommands.isEmpty());
        assertEquals(List.of(Path.of("/zolt/zolt.jar")), workerClasspaths.getFirst());
        QuarkusTestRunnerDescriptor descriptor = descriptors.getFirst();
        assertEquals(projectDir.toAbsolutePath().normalize(), descriptor.projectDirectory());
        assertEquals(projectDir.resolve("target/classes").toAbsolutePath().normalize(), descriptor.mainOutputDirectory());
        assertEquals(projectDir.resolve("target/test-classes").toAbsolutePath().normalize(), descriptor.testOutputDirectory());
        assertEquals(
                projectDir.resolve("target/quarkus/test-application-model.dat").toAbsolutePath().normalize(),
                descriptor.serializedApplicationModel());
        assertTrue(descriptor.jbossLogManagerPresent());
        assertTrue(Files.exists(projectDir.resolve("target/quarkus/zolt-test-bootstrap.properties")));
    }

    @Test
    void quarkusWorkerFailureProducesTestRunError() throws IOException {
        writeConsoleLockfile();
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service(
                (command, outputConsumer) -> new JavaRunner.ProcessResult(0, "direct java should not run\n"),
                (projectDirectory, config) -> Optional.of(projectDirectory
                        .resolve("target/quarkus/test-application-model.dat")
                        .toAbsolutePath()
                        .normalize()),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("worker failed");
                });

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, quarkusConfig(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("worker failed"));
    }

    @Test
    void quarkusBootstrapStackTraceFailsEvenWhenJUnitConsoleExitsZero() throws IOException {
        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> TestRunService.failOnHiddenQuarkusBootstrapFailure(quarkusConfig(), """
                        java.lang.ClassCastException: class io.quarkus.builder.BuildChainBuilder cannot be cast to class io.quarkus.builder.BuildChainBuilder
                            at io.quarkus.test.junit.TestBuildChainFunction$1.accept(TestBuildChainFunction.java:51)

                        Test run finished after 41 ms
                        [         1 tests successful      ]

                        Tests passed
                        """));

        assertTrue(exception.getMessage().contains("Quarkus test bootstrap failed"));
        assertTrue(exception.getMessage().contains("early Quarkus test runner path"));
        assertTrue(exception.getMessage().contains("unsupported Quarkus test bootstrap shape"));
        assertTrue(exception.getMessage().contains("BuildChainBuilder"));
    }

    @Test
    void nonQuarkusProjectDoesNotScanSuccessfulJUnitOutputForQuarkusText() {
        TestRunService.failOnHiddenQuarkusBootstrapFailure(config(), """
                java.lang.ClassCastException: class io.quarkus.builder.BuildChainBuilder cannot be cast to class io.quarkus.builder.BuildChainBuilder
                    at io.quarkus.test.junit.TestBuildChainFunction$1.accept(TestBuildChainFunction.java:51)
                Tests passed
                """);
    }

    @Test
    void quarkusTestAnnotationFailsBeforeJUnitConsoleCanSkipIt() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> TestRunService.failOnUnsupportedQuarkusTests(projectDir, quarkusConfig()));

        assertTrue(exception.getMessage().contains("`@QuarkusTest` execution is not supported"));
        assertTrue(exception.getMessage().contains("dedicated Quarkus test runner"));
        assertTrue(exception.getMessage().contains("com/example/QuarkusHttpTest.class"));
    }

    @Test
    void quarkusTestAnnotationCanPassThroughWhenDescriptorSupportsIt() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        TestRunService.failOnUnsupportedQuarkusTests(projectDir, quarkusConfig(), true);
    }

    @Test
    void nonQuarkusProjectDoesNotRejectQuarkusTestAnnotationText() throws IOException {
        Path testClass = projectDir.resolve("target/test-classes/com/example/QuarkusHttpTest.class");
        Files.createDirectories(testClass.getParent());
        Files.writeString(testClass, "constant-pool:Lio/quarkus/test/junit/QuarkusTest;");

        TestRunService.failOnUnsupportedQuarkusTests(projectDir, config());
    }

    private TestRunService service(JavaRunner.ProcessRunner processRunner) {
        return service(processRunner, new JdkDetector());
    }

    private TestRunService service(JavaRunner.ProcessRunner processRunner, JdkChecker jdkChecker) {
        return service(
                processRunner,
                jdkChecker,
                (projectDirectory, config) -> Optional.empty(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("Quarkus test worker should not run for this test.");
                });
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            TestRunService.QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            java.util.function.Supplier<List<Path>> quarkusTestWorkerClasspath,
            TestRunService.QuarkusTestWorkerRunner quarkusTestWorkerRunner) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                quarkusTestApplicationModelWriter,
                new QuarkusTestRunnerDescriptorWriter(),
                quarkusTestWorkerClasspath,
                quarkusTestWorkerRunner,
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, projectDirectory, testRuntimeClasspath, testOutputDirectory) -> {
                    throw new AssertionError("Plain JUnit worker should not run for this test.");
                },
                false,
                ":");
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            JdkChecker jdkChecker,
            TestRunService.QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            java.util.function.Supplier<List<Path>> quarkusTestWorkerClasspath,
            TestRunService.QuarkusTestWorkerRunner quarkusTestWorkerRunner,
            java.util.function.Supplier<List<Path>> plainJunitWorkerClasspath,
            TestRunService.PlainJunitWorkerRunner plainJunitWorkerRunner,
            boolean plainJunitWorkerEnabled) {
        return new TestRunService(
                new TestCompileService(jdkChecker),
                jdkChecker,
                new JavaRunner(":", processRunner),
                quarkusTestApplicationModelWriter,
                new QuarkusTestRunnerDescriptorWriter(),
                quarkusTestWorkerClasspath,
                quarkusTestWorkerRunner,
                plainJunitWorkerClasspath,
                plainJunitWorkerRunner,
                plainJunitWorkerEnabled,
                ":");
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            TestRunService.QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            java.util.function.Supplier<List<Path>> quarkusTestWorkerClasspath,
            TestRunService.QuarkusTestWorkerRunner quarkusTestWorkerRunner) {
        return service(
                processRunner,
                new JdkDetector(),
                quarkusTestApplicationModelWriter,
                quarkusTestWorkerClasspath,
                quarkusTestWorkerRunner);
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

    private void writeNonStandaloneConsoleLockfile() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "org.apiguardian:apiguardian-api"
                version = "1.1.2"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/apiguardian/apiguardian-api/1.1.2/apiguardian-api-1.1.2.jar"
                dependencies = []

                [[package]]
                id = "org.junit.jupiter:junit-jupiter-engine"
                version = "5.11.4"
                source = "maven-central"
                scope = "test"
                direct = true
                jar = "org/junit/jupiter/junit-jupiter-engine/5.11.4/junit-jupiter-engine-5.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-commons"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-commons/1.11.4/junit-platform-commons-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-console"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-console/1.11.4/junit-platform-console-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-engine"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-engine/1.11.4/junit-platform-engine-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-launcher"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-launcher/1.11.4/junit-platform-launcher-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.junit.platform:junit-platform-reporting"
                version = "1.11.4"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/junit/platform/junit-platform-reporting/1.11.4/junit-platform-reporting-1.11.4.jar"
                dependencies = []

                [[package]]
                id = "org.opentest4j:opentest4j"
                version = "1.3.0"
                source = "maven-central"
                scope = "test"
                direct = false
                jar = "org/opentest4j/opentest4j/1.3.0/opentest4j-1.3.0.jar"
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

    private static ProjectConfig quarkusConfig() {
        return config().withFrameworkSettings(new FrameworkSettings(
                new QuarkusSettings(true, QuarkusPackageMode.FAST_JAR)));
    }

    private static ProjectConfig multiRootConfig() {
        return new ProjectConfig(
                new ProjectMetadata("demo", "0.1.0", "com.example", currentJavaMajorVersion(), Optional.of("com.example.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of("org.junit.platform:junit-platform-console-standalone", "1.11.4"),
                new BuildSettings(
                        "src/main/java",
                        "src/test/java",
                        "target/classes",
                        "target/test-classes",
                        List.of("src/test/java", "src/integration-test/java")));
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }

    private static String launcherClasspath(List<String> command) {
        return commandArgumentAfter(command, "-classpath");
    }

    private static String commandArgumentAfter(List<String> command, String argument) {
        int index = command.indexOf(argument);
        assertTrue(index >= 0, "missing command argument " + argument + " in " + command);
        assertTrue(index + 1 < command.size(), "missing value after command argument " + argument + " in " + command);
        return command.get(index + 1);
    }

    private static String executable(String name) {
        return System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("win")
                ? name + ".exe"
                : name;
    }

    private static final class CachingJdkChecker implements JdkChecker {
        private int detectCalls;
        private int toolchainReads;
        private JdkStatus status;

        @Override
        public JdkStatus detect(String requiredVersion) {
            detectCalls++;
            if (status == null) {
                toolchainReads++;
                Path javaHome = Path.of(System.getProperty("java.home"));
                status = new JdkStatus(
                        Optional.of(javaHome),
                        Optional.of(javaHome.resolve("bin").resolve(executable("java"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("javac"))),
                        Optional.of(javaHome.resolve("bin").resolve(executable("jar"))),
                        Optional.of(requiredVersion),
                        requiredVersion);
            }
            return status;
        }

        int detectCalls() {
            return detectCalls;
        }

        int toolchainReads() {
            return toolchainReads;
        }
    }
}
