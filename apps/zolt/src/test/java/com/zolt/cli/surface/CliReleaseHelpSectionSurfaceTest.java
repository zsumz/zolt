package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliReleaseHelpSectionSurfaceTest {
    @Test
    void publishHelpKeepsReleasePublishingOptionsTogether() {
        CommandResult result = execute("publish", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Publish Zolt-produced artifacts to Maven-compatible repositories.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--context",
                "--directory",
                "--dry-run");
        assertFalse(result.stdout().contains("Arguments:"));
        assertFalse(result.stdout().contains("Output:"));
    }

    @Test
    void publishHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "publish", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt publish\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--context\u001B[0m\u001B[36m <CONTEXT>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--dry-run\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void releaseArchiveHelpKeepsArchiveOptionsTogether() {
        CommandResult result = execute("release-archive", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Assemble a release archive from a native binary.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--binary",
                "--directory",
                "--output",
                "--target");
        assertFalse(result.stdout().contains("Arguments:"));
        assertFalse(result.stdout().contains("Diagnostics:"));
    }

    @Test
    void releaseArchiveHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "release-archive", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt release-archive\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--binary\u001B[0m\u001B[36m <BINARY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--output\u001B[0m\u001B[36m <OUTPUT_DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--target\u001B[0m\u001B[36m <TARGET>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void releaseVerifyHelpPlacesArchiveArgumentBeforeOptions() {
        CommandResult result = execute("release-verify", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Verify release archives by unpacking and smoking the binary.",
                "Usage:",
                "Arguments:",
                "ARCHIVE...",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--work-dir");
        assertFalse(result.stdout().contains("Output:"));
        assertFalse(result.stdout().contains("Diagnostics:"));
    }

    @Test
    void releaseVerifyHelpColorsArgumentsSectionAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "release-verify", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt release-verify\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mARCHIVE...\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--work-dir\u001B[0m\u001B[36m <WORK_DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
