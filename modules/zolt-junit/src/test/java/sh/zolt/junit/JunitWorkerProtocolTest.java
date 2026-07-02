package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.TestSelection;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class JunitWorkerProtocolTest {
    private static final Path OUTPUT = Path.of("target/test-classes");

    @Test
    void formatsAndParsesMinimalRunRequests() {
        String frame = JunitWorkerProtocol.runRequest("request-1", OUTPUT);

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals("RUN\tv=1\tid=request-1\tout=target/test-classes", frame);
        assertEquals(JunitWorkerProtocol.WorkerCommand.RUN, request.command());
        assertEquals("request-1", request.requestId());
        assertEquals("target/test-classes", request.testOutputDirectory());
        assertTrue(request.reportsDirectory().isEmpty());
        assertTrue(request.profileDirectory().isEmpty());
        assertEquals(List.of(), request.events());
        assertTrue(request.testSelection().emptySelection());
    }

    @Test
    void roundTripsClassSelectors() {
        assertSelectionRoundTrips(TestSelection.fromFields(
                List.of("com.example.MainTest", "com.example.OtherTest"),
                List.of(),
                List.of(),
                List.of(),
                List.of()));
    }

    @Test
    void roundTripsMethodSelectors() {
        assertSelectionRoundTrips(TestSelection.fromFields(
                List.of(),
                List.of(
                        new TestSelection.MethodSelector("com.example.MainTest", "runs"),
                        new TestSelection.MethodSelector("com.example.OtherTest", "alsoRuns")),
                List.of(),
                List.of(),
                List.of()));
    }

    @Test
    void roundTripsClassNamePatterns() {
        assertSelectionRoundTrips(TestSelection.fromFields(
                List.of(),
                List.of(),
                List.of("*ServiceTest", "com.example.*"),
                List.of(),
                List.of()));
    }

    @Test
    void roundTripsIncludedAndExcludedTags() {
        assertSelectionRoundTrips(TestSelection.fromFields(
                List.of(),
                List.of(),
                List.of(),
                List.of("fast", "smoke"),
                List.of("slow", "flaky")));
    }

    @Test
    void roundTripsEverySelectionShapeTogether() {
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.MainTest"),
                List.of(new TestSelection.MethodSelector("com.example.OtherTest", "runs")),
                List.of("*ServiceTest", "com.example.Foo,BarTest"),
                List.of("fast"),
                List.of("slow"));

        String frame = JunitWorkerProtocol.runRequest("request-1", OUTPUT, selection);

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);
        assertEquals(selection, request.testSelection());
        // The comma inside `com.example.Foo,BarTest` survives both the codec's `%2C` escape and the
        // frame's outer percent-escaping, so it never collides with the field or list separators.
        assertTrue(frame.contains("classes=com.example.MainTest"), frame);
        assertTrue(frame.contains("methods=com.example.OtherTest"), frame);
        assertTrue(frame.contains("includeTags=fast"), frame);
        assertTrue(frame.contains("excludeTags=slow"), frame);
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
                OUTPUT,
                selection,
                Optional.of(Path.of("target/test-reports")),
                List.of("failed", "skipped"));

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals(Optional.of("target/test-reports"), request.reportsDirectory());
        assertTrue(request.profileDirectory().isEmpty());
        assertEquals(List.of("failed", "skipped"), request.events());
        assertEquals(selection, request.testSelection());
    }

    @Test
    void formatsAndParsesRunRequestsWithProfileDirectory() {
        TestSelection selection = TestSelection.fromFields(
                List.of("com.example.MainTest"),
                List.of(),
                List.of(),
                List.of(),
                List.of());

        String frame = JunitWorkerProtocol.runRequest(
                "request-1",
                OUTPUT,
                selection,
                Optional.empty(),
                List.of(),
                Optional.of(Path.of("target/test-profile")));

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertTrue(request.reportsDirectory().isEmpty());
        assertEquals(Optional.of("target/test-profile"), request.profileDirectory());
        assertEquals(List.of(), request.events());
        assertEquals(selection, request.testSelection());
    }

    @Test
    void formatsAndParsesQuitRequests() {
        String frame = JunitWorkerProtocol.quitRequest("quit-1");

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals("QUIT\tv=1\tid=quit-1", frame);
        assertEquals(JunitWorkerProtocol.WorkerCommand.QUIT, request.command());
        assertEquals("quit-1", request.requestId());
        assertEquals("", request.testOutputDirectory());
        assertTrue(request.reportsDirectory().isEmpty());
        assertTrue(request.profileDirectory().isEmpty());
        assertEquals(List.of(), request.events());
        assertTrue(request.testSelection().emptySelection());
    }

    @Test
    void formatsAndParsesResults() {
        String frame = JunitWorkerProtocol.result("request-1", 2);

        JunitWorkerProtocol.WorkerResult result = JunitWorkerProtocol.parseResult(frame);

        assertEquals("ZOLT_WORKER_RESULT\tid=request-1\texit=2", frame);
        assertEquals("request-1", result.requestId());
        assertEquals(2, result.exitCode());
    }

    @Test
    void fieldOrderIsIgnoredWhenParsing() {
        // Decoding is by name, never by position: a reordered frame decodes identically.
        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(
                "RUN\tid=request-1\tout=target/test-classes\tv=1\tclasses=com.example.MainTest");

        assertEquals("request-1", request.requestId());
        assertEquals("target/test-classes", request.testOutputDirectory());
        assertEquals(List.of("com.example.MainTest"), request.testSelection().classSelectors());
    }

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
    void preservesRequestIdsWithReservedCharacters() {
        // Reserved characters in a value are escaped on the wire and restored on decode, so an id
        // containing a tab or `=` round-trips rather than corrupting the framing.
        String frame = JunitWorkerProtocol.parseRequest(
                        JunitWorkerProtocol.quitRequest("a=b"))
                .requestId();

        assertEquals("a=b", frame);
        assertTrue(JunitWorkerProtocol.quitRequest("a=b").contains("id=a%3Db"), JunitWorkerProtocol.quitRequest("a=b"));
    }

    @Test
    void rejectsMalformedResults() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\tid=request-1\texit=nope"));

        assertTrue(exception.getMessage().contains("Malformed JUnit worker result exit code"), exception.getMessage());
    }

    @Test
    void rejectsResultsMissingExitCode() {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> JunitWorkerProtocol.parseResult("ZOLT_WORKER_RESULT\tid=request-1"));

        assertTrue(exception.getMessage().contains("exit code"), exception.getMessage());
    }

    private static void assertSelectionRoundTrips(TestSelection selection) {
        String frame = JunitWorkerProtocol.runRequest("request-1", OUTPUT, selection);

        JunitWorkerProtocol.WorkerRequest request = JunitWorkerProtocol.parseRequest(frame);

        assertEquals(selection, request.testSelection());
    }
}
