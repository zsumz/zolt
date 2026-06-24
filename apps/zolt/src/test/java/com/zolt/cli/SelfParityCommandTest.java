package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfParityCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void selfParityUsageShowsDirectoryOption() {
        CommandResult result = execute("self-parity", "--help");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("--directory"));
        assertTrue(result.stderr().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stderr().contains("directory."));
    }

    @Test
    void selfParityReadsSelectedDirectory() {
        Path projectDir = tempDir.resolve("selected");

        CommandResult result = execute(
                "self-parity",
                "--directory", projectDir.toString(),
                "--bootstrap-jar", "build/bootstrap-zolt/zolt-bootstrap.jar",
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Self-hosting parity requires bootstrap jar"));
        assertTrue(result.stderr().contains(projectDir.resolve("build/bootstrap-zolt/zolt-bootstrap.jar").toString()));
    }

    @Test
    void selfParityKeepsHiddenCwdCompatibility() {
        Path projectDir = tempDir.resolve("hidden-cwd");

        CommandResult result = execute(
                "self-parity",
                "--cwd", projectDir.toString(),
                "--bootstrap-jar", "build/bootstrap-zolt/zolt-bootstrap.jar",
                "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(projectDir.resolve("build/bootstrap-zolt/zolt-bootstrap.jar").toString()));
    }
}
