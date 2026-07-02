package sh.zolt.cli.surface;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
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
                "Remove project build output.",
                "Usage:",
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
                "--members");
        assertFalse(result.stdout().contains("Diagnostics:"));
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void cleanHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "clean", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mWorkspace Selection:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt clean\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--workspace\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--member\u001B[0m\u001B[36m <MEMBERS>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--members\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
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
                "Smoke a native Zolt binary against real workflows.",
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
                "--work-dir");
        assertFalse(result.stdout().contains("Diagnostics:"));
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void nativeSmokeHelpColorsSectionHeadingAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "native-smoke", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt native-smoke\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--binary\u001B[0m\u001B[36m <BINARY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--work-dir\u001B[0m\u001B[36m <WORK_DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mDiagnostics:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
