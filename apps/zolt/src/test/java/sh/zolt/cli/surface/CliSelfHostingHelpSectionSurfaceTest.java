package sh.zolt.cli.surface;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliSelfHostingHelpSectionSurfaceTest {
    @Test
    void selfCheckHelpGroupsResolutionAndDiagnosticsOptions() {
        CommandResult result = execute("self-check", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Run the self-hosting verification path.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--native",
                "--native-image",
                "Resolution:",
                "--offline",
                "Diagnostics:",
                "--timings",
                "--timings-format");
    }

    @Test
    void selfCheckHelpColorsSectionHeadingsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "self-check", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt self-check\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--native\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--native-image\u001B[0m\u001B[36m <NATIVE_IMAGE_EXECUTABLE>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--offline\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--timings-format\u001B[0m\u001B[36m <FORMAT>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void selfParityHelpKeepsParityOptionsTogether() {
        CommandResult result = execute("self-parity", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Compare bootstrap and Zolt-built jar entries.",
                "Usage:",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--bootstrap-jar",
                "--directory");
        assertFalse(result.stdout().contains("Resolution:"));
        assertFalse(result.stdout().contains("Diagnostics:"));
    }

    @Test
    void selfParityHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "self-parity", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt self-parity\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--bootstrap-jar\u001B[0m\u001B[36m <BOOTSTRAP_JAR>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
