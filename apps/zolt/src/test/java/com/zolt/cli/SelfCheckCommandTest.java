package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfCheckCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void selfCheckUsageShowsDirectoryOption() {
        CommandResult result = execute("self-check", "--help");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("--directory"));
        assertTrue(result.stderr().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stderr().contains("directory."));
    }

    @Test
    void selfCheckReadsSelectedDirectory() {
        Path projectDir = tempDir.resolve("selected");

        CommandResult result = execute(
                "self-check",
                "--directory", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Self-check status: error"));
        assertTrue(result.stdout().contains("error: config - Could not read zolt.toml"));
        assertTrue(result.stdout().contains(projectDir.resolve("zolt.toml").toString()));
    }

    @Test
    void selfCheckKeepsHiddenCwdCompatibility() {
        Path projectDir = tempDir.resolve("hidden-cwd");

        CommandResult result = execute(
                "self-check",
                "--cwd", projectDir.toString(),
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("Self-check status: error"));
        assertTrue(result.stdout().contains(projectDir.resolve("zolt.toml").toString()));
    }
}
