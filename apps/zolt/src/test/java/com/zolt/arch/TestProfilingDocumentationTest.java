package com.zolt.arch;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import org.junit.jupiter.api.Test;

final class TestProfilingDocumentationTest {
    @Test
    void commandsDocumentPlannedProfilingCliAndSchema() throws IOException {
        String commands = Files.readString(RepositoryPaths.root().resolve("docs/commands.md"));

        assertTrue(commands.contains("zolt test --profile-tests"));
        assertTrue(commands.contains("--profile-top <count>"));
        assertTrue(commands.contains("--profile-min <duration>"));
        assertTrue(commands.contains("--profile-dir <path>"));
        assertTrue(commands.contains("separate from command-level `--timings`"));
        assertTrue(commands.contains("\"schemaVersion\": 1"));
        assertTrue(commands.contains("\"durationMillis\""));
        assertTrue(commands.contains("\"workerId\""));
        assertTrue(commands.contains("Unsupported framework-specific runners must fail before test launch"));
    }

    @Test
    void architectureExplainsWorkerNativeProfilingBoundary() throws IOException {
        String architecture = Files.readString(
                RepositoryPaths.root().resolve("docs/test-execution-architecture.md"));

        assertTrue(architecture.contains("Test profiling answers \"which tests and classes consumed the run?\""));
        assertTrue(architecture.contains("Zolt's plain JUnit worker"));
        assertTrue(architecture.contains("TestExecutionListener"));
        assertTrue(architecture.contains("Framework-specific\nrunners should report an actionable unsupported-runner diagnostic"));
        assertTrue(architecture.contains("Parallel workers write worker-local profile files before merge"));
        assertTrue(architecture.contains("Profile-aware shard planning consumes class-level duration history"));
        assertTrue(architecture.contains("--balance-from\n<profile.json>"));
        assertTrue(architecture.contains("unusable, wrong-schema, or\n   malformed"));
        assertTrue(architecture.contains("publishes the planner's deterministic round-robin fallback matrix"));
    }
}
