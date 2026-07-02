package sh.zolt.cli.selfhost;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class SelfCheckCommandTest {
    @TempDir
    private Path tempDir;

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
    void selfCheckUsesModernHumanOutputControls() {
        Path colorProject = tempDir.resolve("color");
        Path quietProject = tempDir.resolve("quiet");

        CommandResult color = execute(
                "--color=always",
                "self-check",
                "--directory", colorProject.toString(),
                "--cache-root", tempDir.resolve("color-cache").toString());
        CommandResult quiet = execute(
                "--quiet",
                "self-check",
                "--directory", quietProject.toString(),
                "--cache-root", tempDir.resolve("quiet-cache").toString());

        assertEquals(1, color.exitCode());
        assertTrue(color.stdout().contains("Self-check status: \u001B[31merror\u001B[0m"));
        assertFalse(color.stdout().contains("\u001B[31mSelf-check\u001B[0m status"));
        assertTrue(color.stdout().contains("\u001B[31merror:\u001B[0m config - Could not read zolt.toml"));
        assertFalse(color.stdout().contains("\u001B[31merror: config"));
        assertEquals(1, quiet.exitCode());
        assertEquals("", quiet.stdout());
        assertTrue(quiet.stderr().contains("Self-check failed."));
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
