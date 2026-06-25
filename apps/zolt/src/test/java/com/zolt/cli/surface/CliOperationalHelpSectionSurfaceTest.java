package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliOperationalHelpSectionSurfaceTest {
    @Test
    void cleanHelpKeepsCleanOptionsTogether() {
        CommandResult result = execute("clean", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Usage:",
                "Remove project build output.",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory");
        assertFalse(result.stdout().contains("Diagnostics:"));
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void cleanHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "clean", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt clean\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <directory>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void nativeSmokeHelpKeepsSmokeOptionsTogether() {
        CommandResult result = execute("native-smoke", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Usage:",
                "Smoke a native Zolt binary against real workflows.",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--binary",
                "--directory",
                "--work-dir");
        assertFalse(result.stdout().contains("Diagnostics:"));
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void nativeSmokeHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "native-smoke", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt native-smoke\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--binary\u001B[0m\u001B[36m <binary>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <directory>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--work-dir\u001B[0m\u001B[36m <workDirectory>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
