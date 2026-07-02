package sh.zolt.cli.surface;

import static sh.zolt.cli.CliTestSupport.execute;
import static sh.zolt.cli.surface.CliHelpSurfaceFixtures.assertContainsInOrder;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
import org.junit.jupiter.api.Test;

final class CliDependencyEditHelpSectionSurfaceTest {
    @Test
    void addHelpGroupsArgumentsAndResolutionOptions() {
        CommandResult result = execute("add", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Add a dependency to zolt.toml and refresh zolt.lock.",
                "Usage:",
                "Arguments:",
                "DEPENDENCY...",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--managed",
                "--version-ref",
                "Resolution:",
                "--no-resolve");
    }

    @Test
    void addHelpColorsArgumentsResolutionAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "add", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt add\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mDEPENDENCY...\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--managed\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--version-ref\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--no-resolve\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void removeHelpGroupsArgumentsAndOptions() {
        CommandResult result = execute("remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Remove a dependency and prune unused transitive packages.",
                "Usage:",
                "Arguments:",
                "DEPENDENCY...",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory");
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void removeHelpColorsArgumentsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt remove\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mDEPENDENCY...\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void versionSetHelpGroupsArgumentsAndResolutionOptions() {
        CommandResult result = execute("version", "set", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Set a version alias in zolt.toml and refresh zolt.lock.",
                "Usage:",
                "Arguments:",
                "ALIAS",
                "VERSION",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "Resolution:",
                "--no-resolve");
    }

    @Test
    void versionSetHelpColorsArgumentsResolutionAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "version", "set", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt version set\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mALIAS\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mVERSION\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--no-resolve\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void versionRemoveHelpGroupsArgumentsAndResolutionOptions() {
        CommandResult result = execute("version", "remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Remove an unused version alias from zolt.toml and refresh zolt.lock.",
                "Usage:",
                "Arguments:",
                "ALIAS",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "Resolution:",
                "--no-resolve");
    }

    @Test
    void versionRemoveHelpColorsArgumentsResolutionAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "version", "remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt version remove\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mALIAS\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--no-resolve\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void platformAddHelpGroupsArgumentsAndResolutionOptions() {
        CommandResult result = execute("platform", "add", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Add a platform BOM import to zolt.toml and refresh zolt.lock.",
                "Usage:",
                "Arguments:",
                "GROUP:ARTIFACT[:VERSION]",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory",
                "--version-ref",
                "Resolution:",
                "--no-resolve");
    }

    @Test
    void platformAddHelpColorsArgumentsResolutionAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "platform", "add", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt platform add\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mGROUP:ARTIFACT[:VERSION]\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--version-ref\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--no-resolve\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }

    @Test
    void platformRemoveHelpGroupsArgumentsAndOptions() {
        CommandResult result = execute("platform", "remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertContainsInOrder(
                result.stdout(),
                "Remove a platform BOM import and refresh zolt.lock.",
                "Usage:",
                "Arguments:",
                "GROUP:ARTIFACT",
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version",
                "--directory");
        assertFalse(result.stdout().contains("Resolution:"));
    }

    @Test
    void platformRemoveHelpColorsArgumentsAndOptionsWithoutWarningColor() {
        CommandResult result = execute("--color=always", "platform", "remove", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1;32mArguments:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;32mOptions:\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36mzolt platform remove\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36mGROUP:ARTIFACT\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[1;36m--directory\u001B[0m\u001B[36m <DIRECTORY>\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32mResolution:\u001B[0m"));
        assertFalse(result.stdout().contains("\u001B[1;32m--"));
        assertFalse(result.stdout().contains("\u001B[33m"));
    }
}
