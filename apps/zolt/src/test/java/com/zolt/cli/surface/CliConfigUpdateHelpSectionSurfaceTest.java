package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliConfigUpdateHelpSectionSurfaceTest {
    @Test
    void configHelpShowsOptionsBeforeConfigCommands() {
        CommandResult result = execute("config", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Usage:",
                "Inspect user-local Zolt config diagnostics.",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "Commands:",
                "show");
    }

    @Test
    void configHelpColorsOptionsAndCommandListWithoutWarningColor() {
        CommandResult result = execute("--color=always", "config", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mCommands\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt config\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--help\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mshow\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void configShowHelpKeepsConfigPathOptionWithDefaults() {
        CommandResult result = execute("config", "show", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Usage:",
                "Show effective user global config settings.",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--config",
                "Defaults to ~/.zolt/config");
        assertFalse(result.stdout().contains("Diagnostics:"));
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void configShowHelpColorsConfigOptionWithoutWarningColor() {
        CommandResult result = execute("--color=always", "config", "show", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt config show\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--config\u001B[0m\u001B[36m=<configPath>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void updateHelpKeepsDefaultOptionsOnly() {
        CommandResult result = execute("update", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Usage:",
                "Update the Zolt executable in place.",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version");
        assertFalse(result.stdout().contains("Commands:"));
        assertFalse(result.stdout().contains("Diagnostics:"));
    }

    @Test
    void updateHelpColorsOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "update", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt update\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--help\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mCommands\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
