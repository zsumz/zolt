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
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Options:",
                "--color",
                "--help",
                "--version",
                "--directory",
                "Workspace Selection:",
                "--workspace",
                "--all",
                "--member",
                "--members",
                "Test Selection:",
                "--test",
                "--tests",
                "--include-tag",
                "--exclude-tag",
                "Test Runtime:",
                "--jvm-arg",
                "Output:",
                "--reports-dir",
                "--test-event",
                "Diagnostics:",
                "--timings",
                "--timings-format");
    }

    @Test
    void testHelpColorsSectionHeadingsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "test", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\u001B[1mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1mWorkspace Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1mTest Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1mDiagnostics\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[32m--color"));
        assertTrue(result.stdout().contains("\u001B[32m--workspace"));
        assertTrue(result.stdout().contains("\u001B[32m--test"));
        assertFalse(result.stdout().contains("\u001B[33m"));
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

    private static void assertContainsInOrder(String text, String... expected) {
        int previousIndex = -1;
        for (String item : expected) {
            int index = text.indexOf(item, previousIndex + 1);
            assertTrue(index > previousIndex, "Expected `" + item + "` after index " + previousIndex);
            previousIndex = index;
        }
    }
}
