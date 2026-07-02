package sh.zolt.cli.surface;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliAuditHelpSectionSurfaceTest {
    @Test
    void planHelpGroupsOptionsAndOutput() {
        CommandResult result = execute("plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Show the typed Zolt command plan without executing it.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--native-image",
                "--target",
                "Output:",
                "--format",
                "--reports-dir");
    }

    @Test
    void planHelpColorsSectionsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "plan", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt plan\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--native-image\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--target\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void policyHelpGroupsOptionsAndOutput() {
        CommandResult result = execute("policy", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Show dependency baseline and policy diagnostics.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "Output:",
                "--format");
    }

    @Test
    void policyHelpColorsSectionsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "policy", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt policy\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void explainHelpGroupsOptionsAndOutput() {
        CommandResult result = execute("explain", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Audit a Maven or Gradle project for future Zolt migration.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--blockers",
                "--directory",
                "--scorecard",
                "--source",
                "Output:",
                "--format");
    }

    @Test
    void explainHelpColorsSectionsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "explain", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt explain\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--blockers\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--scorecard\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--source\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
