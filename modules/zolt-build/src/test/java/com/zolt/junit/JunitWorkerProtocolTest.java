package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
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
        assertTrue(request.reportsDirectory().isEmpty());
        assertEquals(List.of(), request.events());
        assertTrue(request.testSelection().emptySelection());
    }

    @Test
    void formatsAndParsesRunRequestsWithSelection() {
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.MainTest"),
                List.of(new TestSelection.MethodSelector("com.example.OtherTest", "runs")),
                List.of("*ServiceTest", "com.example.Foo,BarTest"),
                List.of("fast"),
                List.of("slow"));

        String frame = JunitWorkerProtocol.runRequest("request-1", Path.of("target/test-classes"), selection);

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals(
                "RUN\trequest-1\ttarget/test-classes\tcom.example.MainTest\tcom.example.OtherTest#runs\t*ServiceTest,com.example.Foo%2CBarTest\tfast\tslow",
                frame);
        assertEquals(selection, request.testSelection());
    }

    @Test
    void formatsAndParsesRunRequestsWithReportsEventsAndSelection() {
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.MainTest"),
                List.of(),
                List.of(),
                List.of("fast"),
                List.of());

        String frame = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.of(Path.of("target/test-reports")),
                List.of("failed", "skipped"));

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals(
                "RUN\trequest-1\ttarget/test-classes\ttarget/test-reports\tfailed,skipped\tcom.example.MainTest\t\t\tfast\t",
                frame);
        assertEquals(Optional.of("target/test-reports"), request.reportsDirectory());
        assertEquals(List.of("failed", "skipped"), request.events());
        assertEquals(selection, request.testSelection());
    }

    @Test
    void formatsAndParsesQuitRequests() {
        String frame = JunitWorkerProtocol.quitRequest("quit-1");

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals("QUIT\tquit-1", frame);
        assertEquals(JunitWorkerProtocol.WorkerCommand.QUIT, request.command());
        assertEquals("quit-1", request.requestId());
        assertEquals("", request.testOutputDirectory());
        assertTrue(request.reportsDirectory().isEmpty());
        assertEquals(List.of(), request.events());
        assertTrue(request.testSelection().emptySelection());
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
    void rejectsMalformedSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest(
                        "RUN\trequest-1\ttarget/test-classes\tcom.example.MainTest\tbad-method\t\t\t"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker test selection"));
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
