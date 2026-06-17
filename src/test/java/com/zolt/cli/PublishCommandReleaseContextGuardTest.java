package com.zolt.cli;

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

final class PublishCommandReleaseContextGuardTest {
    @TempDir
    private Path tempDir;

    @Test
    void publishRejectsContextWithoutDryRun() throws IOException {
        Path projectDir = tempDir.resolve("publish-context-without-dry-run");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), memberConfig("publish-context-without-dry-run"));

        CommandResult result = execute(
                "publish",
                "--context", "release",
                "--cwd", projectDir.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("error: Publish context policy is currently supported only with --dry-run."));
    }
}
