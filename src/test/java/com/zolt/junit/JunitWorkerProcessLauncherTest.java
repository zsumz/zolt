package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;

final class JunitWorkerProcessLauncherTest {
    @Test
    void buildsServerCommandWithWorkerAndTestRuntimeClasspaths() {
        JunitWorkerProcessLauncher launcher = launcher();

        assertEquals(List.of(
                "/jdk/bin/java",
                "-Duser.dir=/repo",
                "-classpath",
                "/zolt/zolt.jar:/repo/target/test-classes:/repo/target/classes:/cache/junit.jar",
                JunitLauncherWorker.MAIN_CLASS,
                "--server"), launcher.command(
                Path.of("/repo"),
                List.of(
                        Path.of("/repo/target/test-classes"),
                        Path.of("/repo/target/classes"),
                        Path.of("/cache/junit.jar"))));
    }

    @Test
    void startsWorkerAndRunsThroughClient() {
        List<List<String>> commands = new ArrayList<>();
        List<Path> directories = new ArrayList<>();
        StringWriter input = new StringWriter();
        AtomicBoolean closed = new AtomicBoolean();
        JunitWorkerProcessLauncher launcher = new JunitWorkerProcessLauncher(
                ":",
                Path.of("/jdk/bin/java"),
                List.of(Path.of("/zolt/zolt.jar")),
                (command, projectDirectory) -> {
                    commands.add(command);
                    directories.add(projectDirectory);
                    return new JunitWorkerProcessLauncher.StartedWorker(
                            new StringReader("""
                                    Tests found: 1
                                    ZOLT_WORKER_RESULT\tjunit-1\t0
                                    ZOLT_WORKER_RESULT\tjunit-2\t0
                                    """),
                            input,
                            () -> closed.set(true));
                });

        try (JunitWorkerProcess process = launcher.start(
                Path.of("/repo"),
                List.of(Path.of("/repo/target/test-classes"), Path.of("/cache/junit.jar")))) {
            JunitWorkerClient.WorkerRunResult result = process.run(Path.of("/repo/target/test-classes"));

            assertEquals("Tests found: 1\n", result.output());
            assertEquals(0, result.exitCode());
        }

        assertEquals(1, commands.size());
        assertEquals(Path.of("/repo"), directories.getFirst());
        assertEquals("""
                RUN\tjunit-1\t/repo/target/test-classes
                QUIT\tjunit-2
                """, input.toString());
        assertTrue(closed.get());
    }

    @Test
    void closesProcessWhenClientCloseFails() {
        AtomicBoolean closed = new AtomicBoolean();
        JunitWorkerProcess process = new JunitWorkerProcess(
                new JunitWorkerClient(
                        new StringReader("ZOLT_WORKER_RESULT\tother\t1\n"),
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
                        new StringReader("ZOLT_WORKER_RESULT\tjunit-1\t0\n"),
                        new StringWriter(),
                        () -> {
                        }));
    }
}
