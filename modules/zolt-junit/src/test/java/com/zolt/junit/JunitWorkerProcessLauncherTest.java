package com.zolt.junit;

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
