package com.zolt.cli.quality;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CheckExecutionContextEvidenceJsonTest {
    @TempDir
    private Path tempDir;

    @Test
    void checkContextCiJsonOutputIsStable() throws IOException {
        Path projectDir = tempDir.resolve("check-context-ci-json");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("check-context-ci-json"));
        Files.writeString(projectDir.resolve("zolt.lock"), "version = 1\n");

        CommandResult result = execute(
                "check",
                "--context", "ci",
                "--check", "execution-context",
                "--format", "json",
                "--cwd", projectDir.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\"id\":\"execution-context\""));
        assertTrue(result.stdout().contains("\"subject\":\"ci\""));
        assertTrue(result.stdout().contains("\"status\":\"passed\""));
        assertTrue(result.stdout().contains("CI context policy is active"));
        assertTrue(result.stdout().contains("Policy source: built-in ci context"));
        assertEquals("", result.stderr());
    }
}
