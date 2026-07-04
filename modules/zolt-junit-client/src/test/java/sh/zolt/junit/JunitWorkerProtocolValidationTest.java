package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JunitWorkerProtocolValidationTest {
    private static final Path OUTPUT = Path.of("target/test-classes");

    @Test
    void rejectsRunRequestsMissingTestOutputDirectory() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=1\tid=request-1"));

        assertTrue(exception.getMessage().contains("test output directory"), exception.getMessage());
        assertTrue(exception.getMessage().contains("is required"), exception.getMessage());
    }

    @Test
    void rejectsRequestsMissingSchemaVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tid=request-1\tout=target/test-classes"));

        assertTrue(exception.getMessage().contains("schema version"), exception.getMessage());
    }

    @Test
    void rejectsMalformedSchemaVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=not-a-number\tid=request-1\tout=target/test-classes"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker schema version"), exception.getMessage());
    }

    @Test
    void rejectsUnsupportedSchemaVersion() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=99\tid=request-1\tout=target/test-classes"));

        assertTrue(exception.getMessage().contains("Unsupported JUnit worker schema version 99"), exception.getMessage());
    }

    @Test
    void rejectsFieldsThatAreNotNameValue() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=1\tid=request-1\tnaked-token"));

        assertTrue(exception.getMessage().contains("not name=value"), exception.getMessage());
    }

    @Test
    void rejectsDuplicateFields() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=1\tid=request-1\tout=a\tout=b"));

        assertTrue(exception.getMessage().contains("duplicate field `out`"), exception.getMessage());
    }

    @Test
    void rejectsUnexpectedQuitFields() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("QUIT\tv=1\tid=request-1\tout=target/test-classes"));

        assertTrue(exception.getMessage().contains("unexpected field `out`"), exception.getMessage());
    }

    @Test
    void rejectsMalformedSelection() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest(
                        "RUN\tv=1\tid=request-1\tout=target/test-classes\tmethods=bad-method"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker test selection"), exception.getMessage());
    }

    @Test
    void rejectsMalformedEventListsWithEventLabel() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest(
                        "RUN\tv=1\tid=request-1\tout=target/test-classes\tevents=failed,,skipped"));

        assertTrue(exception.getMessage().contains("JUnit worker events"), exception.getMessage());
        assertTrue(exception.getMessage().contains("empty value"), exception.getMessage());
    }

    @Test
    void rejectsUnknownRequestCommands() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("STOP\tv=1\tid=request-1"));

        assertTrue(exception.getMessage().contains("Unknown JUnit worker request command"), exception.getMessage());
    }

    @Test
    void rejectsRequestIdsWithTabs() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.runRequest("request\t1", OUTPUT));

        assertTrue(exception.getMessage().contains("JUnit worker request id"), exception.getMessage());
    }

    @Test
    void rejectsDecodedRequestIdsWithControlCharacters() {
        IllegalArgumentException request = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseRequest("RUN\tv=1\tid=request%0A1\tout=target/test-classes"));
        IllegalArgumentException result = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\tid=request%0D1\texit=0"));

        assertTrue(request.getMessage().contains("must not contain tabs or newlines"), request.getMessage());
        assertTrue(result.getMessage().contains("must not contain tabs or newlines"), result.getMessage());
    }

    @Test
    void rejectsBlankRequestIdsAndOutputDirectories() {
        IllegalArgumentException missingId = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.quitRequest(" "));
        IllegalArgumentException nullRunId = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.runRequest(null, OUTPUT));
        IllegalArgumentException nullResultId = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.result(null, 0));
        IllegalArgumentException missingOutput = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.runRequest("request-1", Path.of(" ")));
        IllegalArgumentException nullOutput = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.runRequest("request-1", null));

        assertTrue(missingId.getMessage().contains("request id is required"), missingId.getMessage());
        assertTrue(nullRunId.getMessage().contains("request id is required"), nullRunId.getMessage());
        assertTrue(nullResultId.getMessage().contains("request id is required"), nullResultId.getMessage());
        assertTrue(missingOutput.getMessage().contains("test output directory is required"), missingOutput.getMessage());
        assertTrue(nullOutput.getMessage().contains("test output directory is required"), nullOutput.getMessage());
    }

    @Test
    void rejectsMalformedResults() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\tid=request-1\texit=nope"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker result exit code"), exception.getMessage());
    }

    @Test
    void rejectsResultFramesWithWrongCommand() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("RUN\tid=request-1\texit=0"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker result"), exception.getMessage());
        assertTrue(exception.getMessage().contains(JunitWorkerProtocol.RESULT_PREFIX), exception.getMessage());
    }

    @Test
    void rejectsResultsMissingExitCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\tid=request-1"));

        assertTrue(exception.getMessage().contains("exit code"), exception.getMessage());
    }

    @Test
    void rejectsResultsMissingRequestId() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\texit=0"));

        assertTrue(exception.getMessage().contains("request id"), exception.getMessage());
    }

    @Test
    void workerRequestNormalizesNullableOptionalsAndCopiesEvents() {
        java.util.ArrayList<String> events = new java.util.ArrayList<>();
        events.add("failed");

        JunitWorkerProtocol.WorkerRequest request = new JunitWorkerProtocol.WorkerRequest(
                JunitWorkerProtocol.WorkerCommand.RUN,
                "request-1",
                "target/test-classes",
                null,
                null,
                events,
                TestSelection.empty());
        events.add("skipped");

        assertTrue(request.reportsDirectory().isEmpty());
        assertTrue(request.profileDirectory().isEmpty());
        assertEquals(List.of("failed"), request.events());
        assertThrows(UnsupportedOperationException.class, () -> request.events().add("passed"));
    }

    @Test
    void workerRequestNormalizesNullEventsToEmptyList() {
        JunitWorkerProtocol.WorkerRequest request = new JunitWorkerProtocol.WorkerRequest(
                JunitWorkerProtocol.WorkerCommand.RUN,
                "request-1",
                "target/test-classes",
                Optional.empty(),
                Optional.empty(),
                null,
                TestSelection.empty());

        assertEquals(List.of(), request.events());
    }
}
