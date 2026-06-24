package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliTestFamilyHelpSectionSurfaceTest {
    @Test
    void testHelpGroupsWorkspaceSelectionRuntimeOutputAndDiagnosticsOptions() {
        CommandResult result = execute("test", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
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
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mWorkspace Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mTest Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mTest Runtime\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt test\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--color\u001B[0m\u001B[36m=<colorMode>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m=<directory>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--workspace\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--test\u001B[0m\u001B[36m=<testSelectors>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--jvm-arg\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--reports-dir\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings-format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void coverageHelpGroupsReportControlsUnderOutputOptions() {
        CommandResult result = execute("coverage", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
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
                "Output:",
                "--reports-dir",
                "--test-event",
                "--no-xml",
                "--no-html",
                "--exec-file",
                "--xml-report",
                "--html-dir",
                "Diagnostics:",
                "--timings",
                "--timings-format");
    }

    @Test
    void coverageHelpColorsOutputSectionAndReportOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "coverage", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mWorkspace Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mTest Selection\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mOutput\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;32mDiagnostics\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt coverage\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m=<directory>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--workspace\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--test\u001B[0m\u001B[36m=<testSelectors>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--no-xml\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--xml-report\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--html-dir\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings-format\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
