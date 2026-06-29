package com.zolt.cli.testcmd;

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

    @Test
    void testRejectsInvalidShardBeforeReadingProjectConfig() {
        CommandResult result = execute("test", "--cwd", tempDir.toString(), "--shard", "5/4");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Invalid --shard `5/4`"));
        assertTrue(result.stderr().contains("index must be less than or equal to the total"));
        assertFalse(result.stderr().contains("Could not read zolt.toml"));
    }
}
