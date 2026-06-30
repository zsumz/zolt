package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.test.TestSelection;
import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JunitWorkerClientTest {
    @Test
    void sendsRunRequestAndPreservesOutputBeforeResult() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("Tests found: 1\nTests succeeded: 1\nZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                input);

        JunitWorkerClient.WorkerRunResult result = client.run(Path.of("target/test-classes"));

        assertEquals("RUN\tv=1\tid=junit-1\tout=target/test-classes\n", input.toString());
        assertEquals("Tests found: 1\nTests succeeded: 1\n", result.output());
        assertEquals(0, result.exitCode());
    }

    @Test
    void closeSendsQuitRequest() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                input);

        client.close();

        assertEquals("QUIT\tv=1\tid=junit-1\n", input.toString());
    }

    @Test
    void sendsRunRequestWithReportsAndEvents() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("Tests found: 1\nZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                input);

        JunitWorkerClient.WorkerRunResult result = client.run(
                Path.of("target/test-classes"),
                TestSelection.empty(),
                Optional.of(Path.of("target/test-reports")),
                List.of("failed"));

        assertEquals("""
                RUN\tv=1\tid=junit-1\tout=target/test-classes\treports=target/test-reports\tevents=failed
                """, input.toString());
        assertEquals("Tests found: 1\n", result.output());
        assertEquals(0, result.exitCode());
    }

    @Test
    void rejectsRunAfterClose() {
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tid=junit-1\texit=0\n"),
                new StringWriter());

        client.close();
        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> client.run(Path.of("target/test-classes")));

        assertTrue(exception.getMessage().contains("already closed"));
    }

    @Test
    void failsWhenWorkerExitsBeforeResult() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("Tests found: 1\n"),
                input);

        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> client.run(Path.of("target/test-classes")));

        assertEquals("RUN\tv=1\tid=junit-1\tout=target/test-classes\n", input.toString());
        assertTrue(exception.getMessage().contains("exited before sending a result"));
        assertTrue(exception.getMessage().contains("junit-1"));
        assertTrue(exception.getMessage().contains("Tests found: 1"));
    }

    @Test
    void failsOnMismatchedResultRequestId() {
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tid=junit-2\texit=0\n"),
                new StringWriter());

        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> client.run(Path.of("target/test-classes")));

        assertTrue(exception.getMessage().contains("junit-2"));
        assertTrue(exception.getMessage().contains("junit-1"));
    }
}
