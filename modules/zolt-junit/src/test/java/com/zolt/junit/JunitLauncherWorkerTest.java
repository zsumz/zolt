package com.zolt.junit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.test.TestSelection;
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
    void serverModeCanExitWithoutRunningTests() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream("QUIT\trequest-1\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\trequest-1\t0"), workerOutput);
    }

    @Test
    void serverModeRejectsMalformedRequests() {
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();

        int exitCode = new JunitLauncherWorker().run(
                new String[] {"--server"},
                new ByteArrayInputStream("RUN\trequest-1\n".getBytes(StandardCharsets.UTF_8)),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8),
                new PrintStream(stderr, true, StandardCharsets.UTF_8));

        assertEquals(2, exitCode);
        assertTrue(stderr.toString(StandardCharsets.UTF_8)
                .contains("Malformed JUnit worker run request"));
    }

    @Test
    void serverModeWritesJUnitXmlReports() throws Exception {
        Path reports = tempDir.resolve("reports");
        TestSelection selection = TestSelection.fromFields(
                List.of("com.zolt.junit.JunitWorkerProtocolTest"),
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
                new ByteArrayInputStream((request + "\nQUIT\trequest-2\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\trequest-1\t0"), workerOutput);
        try (Stream<Path> reportFiles = Files.walk(reports)) {
            assertTrue(reportFiles.anyMatch(path -> path.getFileName().toString().endsWith(".xml")));
        }
    }

    @Test
    void serverModeWritesTestProfileJson() throws Exception {
        Path profile = tempDir.resolve("profile");
        TestSelection selection = TestSelection.fromFields(
                List.of("com.zolt.junit.JunitWorkerProtocolTest"),
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
                new ByteArrayInputStream((request + "\nQUIT\trequest-2\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\trequest-1\t0"), workerOutput);
        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"schemaVersion\": 1"), json);
        assertTrue(json.contains("\"runner\": \"zolt-junit-worker\""), json);
        assertTrue(json.contains("\"projectRoot\": \"\""), json);
        assertTrue(json.contains("\"project\": \"\""), json);
        assertTrue(json.contains("\"member\": \"\""), json);
        assertTrue(json.contains("\"suite\": \"\""), json);
        assertTrue(json.contains("\"shard\": \"\""), json);
        assertTrue(json.contains("\"className\": \"com.zolt.junit.JunitWorkerProtocolTest\""), json);
        assertTrue(json.contains("\"status\": \"passed\""), json);
        assertTrue(json.contains("\"durationMillis\""), json);
        assertTrue(json.contains("\"tests\""), json);
        assertTrue(json.contains("\"containers\""), json);
    }

    @Test
    void serverModeWritesTestProfileJsonForFailingTests() throws Exception {
        Path profile = tempDir.resolve("failed-profile");
        TestSelection selection = TestSelection.fromFields(
                List.of("com.zolt.junit.ProfileFailureFixture"),
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
                new ByteArrayInputStream((request + "\nQUIT\trequest-2\n").getBytes(StandardCharsets.UTF_8)),
                new PrintStream(stdout, true, StandardCharsets.UTF_8),
                new PrintStream(new ByteArrayOutputStream(), true, StandardCharsets.UTF_8));

        String workerOutput = stdout.toString(StandardCharsets.UTF_8);
        assertEquals(0, exitCode);
        assertTrue(workerOutput.contains("ZOLT_WORKER_RESULT\trequest-1\t1"), workerOutput);
        String json = Files.readString(profile.resolve("profile.json"));
        assertTrue(json.contains("\"className\": \"com.zolt.junit.ProfileFailureFixture\""), json);
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
