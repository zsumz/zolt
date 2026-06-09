package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.doctor.JdkDetector;
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
        List<String> command = commands.getFirst();
        String userDirProperty = "-Duser.dir=" + projectDir.toAbsolutePath().normalize();
        assertEquals(userDirProperty, command.get(1));
        assertTrue(command.indexOf(userDirProperty) < command.indexOf("-classpath"));
        assertTrue(command.contains("org.junit.platform.console.ConsoleLauncher"));
        assertTrue(command.contains("execute"));
        assertTrue(command.contains("--disable-banner"));
        assertTrue(command.contains("--scan-class-path"));
        assertTrue(command.contains("--details"));
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
        assertTrue(command.stream().anyMatch(value -> value.contains("jboss-logmanager-3.1.2.Final.jar")));
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
        return service(
                processRunner,
                (projectDirectory, config) -> Optional.empty(),
                () -> List.of(Path.of("/zolt/zolt.jar")),
                (javaExecutable, workerClasspath, descriptor) -> {
                    throw new QuarkusAugmentationException("Quarkus test worker should not run for this test.");
                });
    }

    private TestRunService service(
            JavaRunner.ProcessRunner processRunner,
            TestRunService.QuarkusTestApplicationModelWriter quarkusTestApplicationModelWriter,
            java.util.function.Supplier<List<Path>> quarkusTestWorkerClasspath,
            TestRunService.QuarkusTestWorkerRunner quarkusTestWorkerRunner) {
        return new TestRunService(
                new TestCompileService(),
                new JdkDetector(),
                new JavaRunner(":", processRunner),
                quarkusTestApplicationModelWriter,
                new QuarkusTestRunnerDescriptorWriter(),
                quarkusTestWorkerClasspath,
                quarkusTestWorkerRunner,
                ":");
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
}
