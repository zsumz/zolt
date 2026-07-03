package sh.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.test.TestSelection;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JunitLauncherWorkerTest {
    @TempDir
    private Path tempDir;

    @Test
    void missingTestOutputDirectoryIsActionable() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[0],
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("JUnit launcher worker requires exactly one test output directory"));
    }

    @Test
    void oneShotModeReturnsTwoWhenNoTestsAreDiscovered() throws Exception {
        Path emptyOutput = tempDir.resolve("empty-test-output");
        Files.createDirectories(emptyOutput);
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {emptyOutput.toString()},
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(2, exitCode);
        assertTrue(workerOutput.contains("Tests found: 0"), workerOutput);
    }

    @Test
    void serverModeRequiresStdin() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                null,
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("server requires stdin"));
    }

    @Test
    void serverModeCanExitWithoutRunningTests() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream(
                        (JunitWorkerProtocol.quitRequest("request-1") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
    }

    @Test
    void serverModeIgnoresBlankLinesBeforeQuit() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream(
                        ("\n  \n" + JunitWorkerProtocol.quitRequest("request-1") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
    }

    @Test
    void serverModeRejectsMalformedRequests() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream("RUN\tv=1\tid=request-1\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("test output directory"));
    }

    @Test
    void serverModeRunsMethodSelectors() {
        TestSelection selection = TestSelection.fromFields(
                List.of(),
                List.of(new TestSelection.MethodSelector("sh.zolt.junit.FilteredFixture", "fastSelected")),
                List.of(),
                List.of(),
                List.of());
        String request = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.empty(),
                List.of());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream((request + "\n" + JunitWorkerProtocol.quitRequest("request-2") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("Tests found: 1"), workerOutput);
        assertTrue(workerOutput.contains("Tests succeeded: 1"), workerOutput);
        assertTrue(workerOutput.contains("Tests failed: 0"), workerOutput);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
    }

    @Test
    void serverModeAppliesClassNamePatternAndTagFilters() {
        TestSelection selection = TestSelection.fromFields(
                List.of(),
                List.of(),
                List.of("*FilteredFixture"),
                List.of("fast"),
                List.of("slow"));
        String request = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.empty(),
                List.of());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream((request + "\n" + JunitWorkerProtocol.quitRequest("request-2") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("Tests found: 1"), workerOutput);
        assertTrue(workerOutput.contains("Tests succeeded: 1"), workerOutput);
        assertTrue(workerOutput.contains("Tests failed: 0"), workerOutput);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
    }

    @Test
    void serverModeWritesJUnitXmlReports() throws Exception {
        Path reports = tempDir.resolve("reports");
        TestSelection selection = TestSelection.fromFields(
                List.of("sh.zolt.junit.JunitWorkerProtocolTest"),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        String request = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.of(reports),
                List.of());
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream((request + "\n" + JunitWorkerProtocol.quitRequest("request-2") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
        try (Stream<Path> reportFiles = Files.walk(reports)) {
            assertTrue(reportFiles.anyMatch(path -> path.getFileName().toString().endsWith(".xml")));
        }
    }

    @Test
    void serverModeWritesTestProfileJson() throws Exception {
        Path profile = tempDir.resolve("profile");
        TestSelection selection = TestSelection.fromFields(
                List.of("sh.zolt.junit.JunitWorkerProtocolTest"),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        String request = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.empty(),
                List.of(),
                Optional.of(profile));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream((request + "\n" + JunitWorkerProtocol.quitRequest("request-2") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=0"), workerOutput);
        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"schemaVersion\": 1"), json);
        assertTrue(json.contains("\"runner\": \"zolt-junit-worker\""), json);
        assertTrue(json.contains("\"projectRoot\": \"\""), json);
        assertTrue(json.contains("\"project\": \"\""), json);
        assertTrue(json.contains("\"member\": \"\""), json);
        assertTrue(json.contains("\"suite\": \"\""), json);
        assertTrue(json.contains("\"shard\": \"\""), json);
        assertTrue(json.contains("\"className\": \"sh.zolt.junit.JunitWorkerProtocolTest\""), json);
        assertTrue(json.contains("\"status\": \"passed\""), json);
        assertTrue(json.contains("\"durationMillis\""), json);
        assertTrue(json.contains("\"tests\""), json);
        assertTrue(json.contains("\"containers\""), json);
    }

    @Test
    void serverModeWritesTestProfileJsonForFailingTests() throws Exception {
        Path profile = tempDir.resolve("failed-profile");
        TestSelection selection = TestSelection.fromFields(
                List.of("sh.zolt.junit.ProfileFailureFixture"),
                List.of(),
                List.of(),
                List.of(),
                List.of());
        String request = JunitWorkerProtocol.runRequest(
                "request-1",
                Path.of("target/test-classes"),
                selection,
                Optional.empty(),
                List.of(),
                Optional.of(profile));
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream((request + "\n" + JunitWorkerProtocol.quitRequest("request-2") + "\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\tid=request-1\texit=1"), workerOutput);
        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"className\": \"sh.zolt.junit.ProfileFailureFixture\""), json);
        assertTrue(json.contains("\"methodName\": \"failsForProfileEvidence\""), json);
        assertTrue(json.contains("\"status\": \"failed\""), json);
        assertTrue(json.contains("\"testsFailed\": 1"), json);
    }
}

final class ProfileFailureFixture {
    @Test
    void failsForProfileEvidence() {
        org.junit.jupiter.api.Assertions.fail("profile failure");
    }
}

final class FilteredFixture {
    @Test
    @org.junit.jupiter.api.Tag("fast")
    void fastSelected() {
    }

    @Test
    @org.junit.jupiter.api.Tag("slow")
    void slowFailure() {
        org.junit.jupiter.api.Assertions.fail("slow test should be filtered");
    }

    @Test
    void untaggedFailure() {
        org.junit.jupiter.api.Assertions.fail("untagged test should be filtered");
    }
}
