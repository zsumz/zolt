package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

final class JunitWorkerProcessLauncherTest {
    @Test
    void buildsServerCommandWithWorkerAndTestRuntimeClasspaths() {
        JunitWorkerProcessLauncher launcher = launcher();

        assertEquals(List.of(
                "/jdk/bin/java",
                "-Dlibrary.mode=true",
                "-Duser.dir=/repo",
                "-classpath",
                "/zolt/zolt.jar:/repo/target/test-classes:/repo/target/classes:/cache/junit.jar",
                JunitLauncherWorker.MAIN_CLASS,
                "--server"), launcher.command(
                Path.of("/repo"),
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit.jar")),
                List.of("-Dlibrary.mode=true")));
    }

    @Test
    void buildsServerCommandWithNullJvmArguments() {
        JunitWorkerProcessLauncher launcher = launcher();

        assertEquals(List.of(
                "/jdk/bin/java",
                "-Duser.dir=/repo",
                "-classpath",
                "/zolt/zolt.jar:/repo/target/test-classes",
                JunitLauncherWorker.MAIN_CLASS,
                "--server"), launcher.command(
                Path.of("/repo"),
                List.of(Path.of("/repo/target/test-classes")),
                null));
    }

    @Test
    void defensivelyCopiesWorkerClasspath() {
        List<Path> workerClasspath = new ArrayList<>();
        workerClasspath.add(Path.of("/zolt/zolt.jar"));
        JunitWorkerProcessLauncher launcher = new JunitWorkerProcessLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                workerClasspath,
                (command, projectDirectory) -> new JunitWorkerProcessLauncher.StartedWorker(
                        new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                        new StringWriter(),
                        () -> {
                        }));
        workerClasspath.add(Path.of("/zolt/late.jar"));

        List<String> command = launcher.command(Path.of("/repo"), List.of(Path.of("/repo/target/test-classes")));

        assertTrue(command.get(3).contains("/zolt/zolt.jar"), command.toString());
        assertTrue(!command.get(3).contains("/zolt/late.jar"), command.toString());
    }

    @Test
    void startsWorkerAndRunsThroughClient() {
        List<List<String>> commands = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        List<Map<String, String>> environments = new ArrayList<>();
        StringWriter input = new StringWriter();
        AtomicBoolean closed = new AtomicBoolean();
        JunitWorkerProcessLauncher launcher = new JunitWorkerProcessLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                new JunitWorkerProcessLauncher.ProcessStarter() {
                    @Override
                    public JunitWorkerProcessLauncher.StartedWorker start(List<String> command, Path projectDirectory) {
                        throw new AssertionError("Environment-aware worker starter should be used.");
                    }

                    @Override
                    public JunitWorkerProcessLauncher.StartedWorker start(
                            List<String> command,
                            Path projectDirectory,
                            Map<String, String> environment) {
                        commands.add(command);
                        directories.add(projectDirectory);
                        environments.add(environment);
                        return new JunitWorkerProcessLauncher.StartedWorker(
                                new StringReader("""
                                        Tests found: 1
                                        ZOLT_WORKER_RESULT\tid=junit-1\texit=0
                                        ZOLT_WORKER_RESULT\tid=junit-2\texit=0
                                        """),
                                input,
                                () -> closed.set(true));
                    }
                });

        try (JunitWorkerProcess process = launcher.start(
                Path.of("/repo"),
                List.of(Path.of("/repo/target/test-classes"), Path.of("/cache/junit.jar")),
                List.of(),
                Map.of("TZ", "America/Chicago"))) {
            JunitWorkerClient.WorkerRunResult result = process.run(Path.of("/repo/target/test-classes"));

            assertEquals("Tests found: 1\n", result.output());
            assertEquals(0, result.exitCode());
        }

        assertEquals(1, commands.size());
        assertEquals(Path.of("/repo"), directories.getFirst());
        assertEquals(Map.of("TZ", "America/Chicago"), environments.getFirst());
        assertEquals("""
                RUN\tv=1\tid=junit-1\tout=/repo/target/test-classes
                QUIT\tv=1\tid=junit-2
                """, input.toString());
        assertTrue(closed.get());
    }

    @Test
    void startsWorkerThroughLegacyStarterWhenEnvironmentIsNull() {
        List<List<String>> commands = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        StringWriter input = new StringWriter();
        JunitWorkerProcessLauncher launcher = new JunitWorkerProcessLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                (command, projectDirectory) -> {
                    commands.add(command);
                    directories.add(projectDirectory);
                    return new JunitWorkerProcessLauncher.StartedWorker(
                            new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                            input,
                            () -> {
                            });
                });

        try (JunitWorkerProcess ignored = launcher.start(
                Path.of("/repo"),
                List.of(Path.of("/repo/target/test-classes")),
                null,
                null)) {
            // Closing sends the quit request and proves the legacy starter returned a usable worker.
        }

        assertEquals(1, commands.size());
        assertEquals(Path.of("/repo"), directories.getFirst());
        assertEquals("QUIT\tv=1\tid=junit-1\n", input.toString());
    }

    @Test
    void closesProcessWhenClientCloseFails() {
        AtomicBoolean closed = new AtomicBoolean();
        JunitWorkerProcess process = new JunitWorkerProcess(
                new JunitWorkerClient(
                        new StringReader("ZOLT_WORKER_RESULT\tid=other\texit=1\n"),
                        new StringWriter()),
                () -> closed.set(true));

        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                process::close);

        assertTrue(exception.getMessage().contains("other"));
        assertTrue(closed.get());
    }

    @Test
    void requiresTestRuntimeClasspath() {
        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> launcher().start(Path.of("/repo"), List.of()));

        assertTrue(exception.getMessage().contains("test runtime classpath is required"));
    }

    @Test
    void validatesConstructorArguments() {
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(null, Path.of("/jdk/bin/java"), List.of(Path.of("/zolt/zolt.jar")), starter()),
                "path separator is required");
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(" ", Path.of("/jdk/bin/java"), List.of(Path.of("/zolt/zolt.jar")), starter()),
                "path separator is required");
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(":", null, List.of(Path.of("/zolt/zolt.jar")), starter()),
                "Java executable is required");
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(":", Path.of("/jdk/bin/java"), null, starter()),
                "classpath is required");
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(":", Path.of("/jdk/bin/java"), List.of(), starter()),
                "classpath is required");
        assertInvalidLauncher(
                () -> new JunitWorkerProcessLauncher(":", Path.of("/jdk/bin/java"), List.of(Path.of("/zolt/zolt.jar")), null),
                "process starter is required");
    }

    @Test
    void requiresProjectDirectory() {
        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> launcher().start(null, List.of(Path.of("/repo/target/test-classes"))));

        assertTrue(exception.getMessage().contains("project directory is required"), exception.getMessage());
    }

    @Test
    void closesProcessOnlyOnce() {
        AtomicInteger closes = new AtomicInteger();
        JunitWorkerProcess process = new JunitWorkerProcess(
                new JunitWorkerClient(
                        new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                        new StringWriter()),
                closes::incrementAndGet);

        process.close();
        process.close();

        assertEquals(1, closes.get());
    }

    @Test
    void rejectsRunAfterProcessClose() {
        JunitWorkerProcess process = new JunitWorkerProcess(
                new JunitWorkerClient(
                        new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                        new StringWriter()),
                () -> {
                });

        process.close();
        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> process.run(Path.of("/repo/target/test-classes")));

        assertTrue(exception.getMessage().contains("process is already closed"), exception.getMessage());
    }

    @Test
    void validatesProcessConstructorArguments() {
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                new StringWriter());

        IllegalArgumentException missingClient = assertThrows(
                IllegalArgumentException.class,
                () -> new JunitWorkerProcess(null, () -> {
                }));
        IllegalArgumentException missingCloser = assertThrows(
                IllegalArgumentException.class,
                () -> new JunitWorkerProcess(client, null));

        assertTrue(missingClient.getMessage().contains("client is required"), missingClient.getMessage());
        assertTrue(missingCloser.getMessage().contains("process closer is required"), missingCloser.getMessage());
    }

    private static void assertInvalidLauncher(Runnable action, String message) {
        JunitWorkerClientException exception = assertThrows(JunitWorkerClientException.class, action::run);

        assertTrue(exception.getMessage().contains(message), exception.getMessage());
    }

    private static JunitWorkerProcessLauncher.ProcessStarter starter() {
        return (command, projectDirectory) -> new JunitWorkerProcessLauncher.StartedWorker(
                new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                new StringWriter(),
                () -> {
                });
    }

    private static JunitWorkerProcessLauncher launcher() {
        return new JunitWorkerProcessLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                (command, projectDirectory) -> new JunitWorkerProcessLauncher.StartedWorker(
                        new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                        new StringWriter(),
                        () -> {
                        }));
    }
}
