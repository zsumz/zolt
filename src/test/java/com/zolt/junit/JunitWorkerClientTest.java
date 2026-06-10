package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.StringReader;
import java.io.StringWriter;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class JunitWorkerClientTest {
    @Test
    void sendsRunRequestAndPreservesOutputBeforeResult() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("Tests found: 1\nTests succeeded: 1\nZOLT_WORKER_RESULT\tjunit-1\t0\n"),
                input);

        JunitWorkerClient.WorkerRunResult result = client.run(Path.of("target/test-classes"));

        assertEquals("RUN\tjunit-1\ttarget/test-classes\n", input.toString());
        assertEquals("Tests found: 1\nTests succeeded: 1\n", result.output());
        assertEquals(0, result.exitCode());
    }

    @Test
    void closeSendsQuitRequest() {
        StringWriter input = new StringWriter();
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tjunit-1\t0\n"),
                input);

        client.close();

        assertEquals("QUIT\tjunit-1\n", input.toString());
    }

    @Test
    void rejectsRunAfterClose() {
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tjunit-1\t0\n"),
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

        assertEquals("RUN\tjunit-1\ttarget/test-classes\n", input.toString());
        assertTrue(exception.getMessage().contains("exited before sending a result"));
        assertTrue(exception.getMessage().contains("junit-1"));
    }

    @Test
    void failsOnMismatchedResultRequestId() {
        JunitWorkerClient client = new JunitWorkerClient(
                new StringReader("ZOLT_WORKER_RESULT\tjunit-2\t0\n"),
                new StringWriter());

        JunitWorkerClientException exception = assertThrows(
                JunitWorkerClientException.class,
                () -> client.run(Path.of("target/test-classes")));

        assertTrue(exception.getMessage().contains("junit-2"));
        assertTrue(exception.getMessage().contains("junit-1"));
    }
}
