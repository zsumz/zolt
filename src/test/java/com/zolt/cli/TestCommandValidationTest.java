package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class TestCommandValidationTest {
    @TempDir
    private Path tempDir;

    @Test
    void testHelpShowsSelectionOptions() {
        CommandResult result = execute("test", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--test"));
        assertTrue(result.stdout().contains("--tests"));
        assertTrue(result.stdout().contains("--include-tag"));
        assertTrue(result.stdout().contains("--exclude-tag"));
        assertTrue(result.stdout().contains("--jvm-arg"));
        assertTrue(result.stdout().contains("--test-event"));
        assertTrue(result.stdout().contains("--reports-dir"));
    }

    @Test
    void extractedTestCommandPreservesSelectionErrorAndExitCodeBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--test", "*ServiceTest");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Invalid --test selector `*ServiceTest`"));
        assertTrue(result.stderr().contains("Use --tests for class-name patterns"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void testRejectsInvalidJvmArgBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--jvm-arg", "-classpath");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Zolt owns the test classpath"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }

    @Test
    void testRejectsInvalidEventBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--test-event", "verbose");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Unsupported test runtime event `verbose`"));
        assertTrue(result.stderr().contains("passed, skipped, failed"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }
}
