package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliQuarkusHelpSectionSurfaceTest {
    @Test
    void quarkusPlanHelpGroupsDiagnosticsOptions() {
        CommandResult result = execute("quarkus", "plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Print the Quarkus augmentation input plan.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "Diagnostics:",
                "--timings",
                "--timings-format");
        assertFalse(result.stdout().contains("Resolution:"));
        assertFalse(result.stdout().contains("Output:"));
    }

    @Test
    void quarkusPlanHelpColorsSectionHeadingsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "quarkus", "plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt quarkus plan\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings-format\u001B[0m\u001B[36m <format>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void quarkusTestPlanHelpKeepsPlanOptionsTogether() {
        CommandResult result = execute("quarkus", "test-plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Print the Quarkus test bootstrap plan.",
                "Usage:",
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
    void quarkusTestPlanHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "quarkus", "test-plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt quarkus test-plan\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
