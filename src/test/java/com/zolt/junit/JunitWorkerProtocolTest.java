package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class JunitWorkerProtocolTest {
    @Test
    void formatsAndParsesRunRequests() {
        String frame = JunitWorkerProtocol.runRequest("request-1", Path.of("target/test-classes"));

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals("RUN\trequest-1\ttarget/test-classes", frame);
        assertEquals(JunitWorkerProtocol.WorkerCommand.RUN, request.command());
        assertEquals("request-1", request.requestId());
        assertEquals("target/test-classes", request.testOutputDirectory());
    }

    @Test
    void formatsAndParsesQuitRequests() {
        String frame = JunitWorkerProtocol.quitRequest("quit-1");

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals("QUIT\tquit-1", frame);
        assertEquals(JunitWorkerProtocol.WorkerCommand.QUIT, request.command());
        assertEquals("quit-1", request.requestId());
        assertEquals("", request.testOutputDirectory());
    }

    @Test
    void formatsAndParsesResults() {
        String frame = JunitWorkerProtocol.result("request-1", 2);

        JunitWorkerProtocol.WorkerResult result = JunitWorkerProtocol.parseResult(frame);

        assertEquals("ZOLT_WORKER_RESULT\trequest-1\t2", frame);
        assertEquals("request-1", result.requestId());
        assertEquals(2, result.exitCode());
    }

    @Test
    void rejectsMalformedRunRequests() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\trequest-1"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker run request"));
    }

    @Test
    void rejectsUnknownRequestCommands() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("STOP\trequest-1"));

        assertTrue(exception.getMessage().contains("Unknown JUnit worker request command"));
    }

    @Test
    void rejectsRequestIdsWithTabs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.runRequest("request\t1", Path.of("target/test-classes")));

        assertTrue(exception.getMessage().contains("JUnit worker request id must not contain tabs or newlines"));
    }

    @Test
    void rejectsParsedRequestIdsWithCarriageReturns() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("QUIT\trequest-1\r"));

        assertTrue(exception.getMessage().contains("JUnit worker request id must not contain tabs or newlines"));
    }

    @Test
    void rejectsMalformedResults() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\trequest-1\tnope"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker result exit code"));
    }
}
