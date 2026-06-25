package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliQualityHelpSectionSurfaceTest {
    @Test
    void checkHelpGroupsWorkspaceOutputResolutionAndDiagnosticsOptions() {
        CommandResult result = execute("check", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Run Zolt-owned quality checks.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--check",
                "--context",
                "--directory",
                "--require-offline-ready",
                "--require-package",
                "--require-publish-dry-run",
                "Workspace Selection:",
                "--workspace",
                "--all",
                "--member",
                "--members",
                "Output:",
                "--format",
                "--reports-dir",
                "--coverage-dir",
                "Resolution:",
                "--offline",
                "Diagnostics:",
                "--timings",
                "--timings-format");
    }

    @Test
    void checkHelpColorsSectionHeadingsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "check", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mWorkspace Selection:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt check\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <directory>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--require-package\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--workspace\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--format\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--offline\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings-format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void doctorHelpGroupsHealthOptionsOnly() {
        CommandResult result = execute("doctor", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Inspect local Java/JDK/Zolt project health.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--self-hosting");
        assertFalse(result.stdout().contains("Output:"));
        assertFalse(result.stdout().contains("Diagnostics:"));
    }

    @Test
    void doctorHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "doctor", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt doctor\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <directory>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--self-hosting\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mOutput:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
