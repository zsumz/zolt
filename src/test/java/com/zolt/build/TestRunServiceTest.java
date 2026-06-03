package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.classpath.ClasspathBuilder;
import com.zolt.doctor.JdkDetector;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.project.BuildSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
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
    void missingConsoleJarProducesActionableError() throws IOException {
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");
        source("src/main/java/com/example/Main.java", "package com.example; public final class Main {}\n");
        source("src/test/java/com/example/MainTest.java", "package com.example; public final class MainTest {}\n");
        TestRunService service = service((command, outputConsumer) -> new JavaRunner.ProcessResult(0, ""));

        TestRunException exception = assertThrows(
                TestRunException.class,
                () -> service.runTests(projectDir, config(), projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("JUnit Platform Console is not present"));
        assertTrue(exception.getMessage().contains("junit-platform-console-standalone"));
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

    private TestRunService service(JavaRunner.ProcessRunner processRunner) {
        return new TestRunService(
                new TestCompileService(),
                new ZoltLockfileReader(),
                new ClasspathBuilder(),
                new JdkDetector(),
                new JavaRunner(":", processRunner),
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
