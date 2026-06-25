package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.memberConfig;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CoverageCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void coverageRejectsDisablingAllReportFormats() throws IOException {
        Path projectDir = tempDir.resolve("coverage-no-reports");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("coverage-no-reports"));

        CommandResult result = execute(
                "coverage",
                "--no-xml",
                "--no-html",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Coverage requires at least one report format"));
    }

    @Test
    void coverageAcceptsVisibleProjectDirectoryOption() throws IOException {
        Path projectDir = tempDir.resolve("coverage-directory");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("coverage-directory"));

        CommandResult result = execute(
                "coverage",
                "--no-xml",
                "--no-html",
                "--directory", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Coverage requires at least one report format"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void coverageRejectsInvalidShardBeforeReadingProject() {
        CommandResult result = execute(
                "coverage",
                "--shard", "5/4",
                "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Invalid --shard `5/4`"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }
}
