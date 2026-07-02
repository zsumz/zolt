package sh.zolt.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestProfileHistoryTest {
    @TempDir
    private Path projectDir;

    @Test
    void readsClassDurationsFromProfileContainers() throws IOException {
        Path profile = projectDir.resolve("target/profile.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, """
                {
                  "schemaVersion": 1,
                  "tests": [],
                  "containers": [
                    {
                      "className": "com.example.FastTest",
                      "durationMillis": 25,
                      "testCount": 2
                    },
                    {
                      "className": "com.example.FastTest",
                      "durationMillis": 30,
                      "testCount": 2
                    },
                    {
                      "className": "com.example.SlowTest",
                      "durationMillis": 1200,
                      "testCount": 1
                    }
                  ]
                }
                """);

        TestProfileHistory history = TestProfileHistory.read(projectDir, Path.of("target/profile.json"));

        assertEquals(profile.toAbsolutePath().normalize(), history.source().orElseThrow());
        assertEquals(55L, history.classDurations().get("com.example.FastTest"));
        assertEquals(1200L, history.classDurations().get("com.example.SlowTest"));
        assertTrue(history.diagnostics().isEmpty());
    }

    @Test
    void missingProfileFallsBackWithDiagnostic() {
        TestProfileHistory history = TestProfileHistory.read(projectDir, Path.of("target/missing-profile.json"));

        assertTrue(history.classDurations().isEmpty());
        assertTrue(history.diagnostics().getFirst().contains("does not exist"));
        assertTrue(history.diagnostics().getFirst().contains("round-robin"));
    }

    @Test
    void unsupportedSchemaFallsBackWithDiagnostic() throws IOException {
        Path profile = projectDir.resolve("target/profile.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, """
                {
                  "schemaVersion": 2,
                  "tests": [],
                  "containers": [
                    {
                      "className": "com.example.SlowTest",
                      "durationMillis": 1200
                    }
                  ]
                }
                """);

        TestProfileHistory history = TestProfileHistory.read(projectDir, Path.of("target/profile.json"));

        assertTrue(history.classDurations().isEmpty());
        assertTrue(history.diagnostics().getFirst().contains("unsupported schemaVersion"));
        assertTrue(history.diagnostics().getFirst().contains("round-robin"));
    }

    @Test
    void malformedProfileFallsBackWithDiagnostic() throws IOException {
        Path profile = projectDir.resolve("target/profile.json");
        Files.createDirectories(profile.getParent());
        Files.writeString(profile, """
                {
                  "schemaVersion": 1,
                  "containers": [
                    {
                      "className": "com.example.SlowTest",
                      "durationMillis": 1200
                    }
                  ]
                }
                """);

        TestProfileHistory history = TestProfileHistory.read(projectDir, Path.of("target/profile.json"));

        assertTrue(history.classDurations().isEmpty());
        assertTrue(history.diagnostics().getFirst().contains("missing tests or containers arrays"));
        assertTrue(history.diagnostics().getFirst().contains("round-robin"));
    }
}
