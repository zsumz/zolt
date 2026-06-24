package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class CliSurfaceTest {
    @TempDir
    private Path tempDir;

    @Test
    void updateExplainsFutureSelfUpdatePath() {
        CommandResult result = execute("update");

        assertEquals(1, result.exitCode());
        assertTrue(result.stdout().contains("zolt update is not available yet."));
        assertTrue(result.stdout().contains("verified native archive"));
        assertTrue(result.stdout().contains("followUps/-design-zolt-update-command.md"));
        assertEquals("", result.stderr());
    }

    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains("\u001B["));
        assertTrue(result.stdout().contains("The modern Java build toolkit."));
        assertTrue(result.stdout().contains("--color"));
        assertTrue(result.stdout().contains("--progress"));
        assertTrue(result.stdout().contains("--no-progress"));
        assertTrue(result.stdout().contains("--quiet"));
        assertTrue(result.stdout().contains("--list"));
        assertContainsInOrder(
                result.stdout(),
                "Commands:",
                "  Basics",
                "    init",
                "    config",
                "    doctor",
                "  Dependencies",
                "    resolve",
                "    conflicts",
                "  Build, Test, Run",
                "    build",
                "    integration-test",
                "  Insight and Tooling",
                "    check",
                "    quarkus",
                "  Native and Release",
                "    native",
                "    release-verify",
                "  Self-Hosting",
                "    self-check");
        assertTrue(result.stdout().contains("help                Display help for zolt or a command."));
        assertFalse(result.stdout().contains("%n"));
    }

    @Test
    void helpSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\u001B[1mBasics\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36minit\u001B[0m"));
        assertTrue(result.stdout().contains("Create a new Zolt project."));
        assertFalse(result.stderr().contains("\u001B["));
    }

    @Test
    void colorNeverKeepsHelpAnsiFree() {
        CommandResult result = execute("--color=never", "help");

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains("\u001B["));
        assertTrue(result.stdout().contains("  Basics"));
        assertTrue(result.stdout().contains("    init                Create a new Zolt project."));
    }

    @Test
    void listShowsGroupedCommandInventoryWithoutUsage() {
        CommandResult result = execute("--list");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("\u001B["));
        assertFalse(result.stdout().contains("Usage:"));
        assertContainsInOrder(
                result.stdout(),
                "Commands:",
                "  Basics",
                "    help",
                "    init",
                "  Dependencies",
                "    resolve",
                "  Build, Test, Run",
                "    build",
                "  Insight and Tooling",
                "    check",
                "  Native and Release",
                "    native",
                "  Self-Hosting",
                "    self-check");
    }

    @Test
    void listSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "--list");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\u001B[1mBasics\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36minit\u001B[0m"));
        assertFalse(result.stderr().contains("\u001B["));
    }

    @Test
    void colorAlwaysDoesNotColorJsonOutput() throws Exception {
        Files.writeString(tempDir.resolve("zolt.toml"), CliTestSupport.memberConfig("json-output"));

        CommandResult result = execute(
                "--color=always",
                "plan",
                "--cwd", tempDir.toString(),
                "--format", "json");

        assertEquals(1, result.exitCode());
        assertFalse(result.stdout().contains("\u001B["));
        assertTrue(result.stdout().contains("\"target\": \"package\""));
        assertTrue(result.stdout().contains("\"status\": \"blocked\""));
    }

    @Test
    void invalidColorModeFailsBeforeCommandExecution() {
        CommandResult result = execute("--color=rainbow", "help");

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("Invalid value for option '--color'"));
        assertTrue(result.stderr().contains("expected one of: auto, always, never"));
    }

    @Test
    void progressOptionsAreGlobalAndValidationMatchesColorMode() {
        CommandResult forced = execute("--progress=always", "help");
        CommandResult disabled = execute("--no-progress", "help");
        CommandResult quiet = execute("--quiet", "help");
        CommandResult invalid = execute("--progress=sparkles", "help");

        assertEquals(0, forced.exitCode());
        assertEquals("", forced.stderr());
        assertTrue(forced.stdout().contains("The modern Java build toolkit."));
        assertEquals(0, disabled.exitCode());
        assertEquals("", disabled.stderr());
        assertTrue(disabled.stdout().contains("The modern Java build toolkit."));
        assertEquals(0, quiet.exitCode());
        assertEquals("", quiet.stderr());
        assertTrue(quiet.stdout().contains("The modern Java build toolkit."));
        assertEquals(2, invalid.exitCode());
        assertTrue(invalid.stderr().contains("Invalid value for option '--progress'"));
        assertTrue(invalid.stderr().contains("expected one of: auto, always, never"));
    }

    @Test
    void quietKeepsFailuresActionable() {
        CommandResult result = execute("--quiet", "resolve", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertEquals("", result.stdout());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("File: " + tempDir.resolve("zolt.toml")));
        assertTrue(result.stderr().contains("Next: Check that the file exists"));
    }

    @Test
    void initCreatesProjectAndPrintsNextCommand() {
        CommandResult result = execute("init", "--cwd", tempDir.toString(), "hello");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Created Zolt project at"));
        assertTrue(result.stdout().contains("Next: cd hello"));
        assertTrue(Files.exists(tempDir.resolve("hello/zolt.toml")));
    }

    @Test
    void packageReportsConfigErrorsCleanly() {
        CommandResult result = execute("package", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
    }

    @Test
    void resolveReportsConfigErrorsCleanly() {
        CommandResult result = execute("resolve", "--cwd", tempDir.toString(), "--cache-root", tempDir.resolve("cache").toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertEquals(1, occurrences(result.stderr(), "error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("File: " + tempDir.resolve("zolt.toml")));
        assertTrue(result.stderr().contains("Next: Check that the file exists"));
    }

    @Test
    void failedCommandStillPrintsTimingsWhenRequested() {
        CommandResult result = execute("resolve", "--timings", "--cwd", tempDir.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("error: Could not read zolt.toml"));
        assertTrue(result.stderr().contains("Timings for zolt resolve"));
        assertTrue(result.stderr().contains("config read:"));
        assertTrue(result.stderr().contains("status=failed"));
    }

    @Test
    void registersMvpCommandSurface() {
        Set<String> subcommands = ZoltCli.newCommandLine().getSubcommands().keySet();

        assertTrue(subcommands.containsAll(Set.of(
                "help",
                "init",
                "version",
                "update",
                "config",
                "check",
                "add",
                "remove",
                "platform",
                "resolve",
                "tree",
                "why",
                "policy",
                "conflicts",
                "explain",
                "plan",
                "classpath",
                "ide",
                "quarkus",
                "build",
                "run",
                "test",
                "integration-test",
                "coverage",
                "package",
                "publish",
                "run-package",
                "native",
                "native-smoke",
                "release-archive",
                "release-verify",
                "self-check",
                "self-parity",
                "clean",
                "doctor")));
        assertEquals(commandClass("classpath"), "com.zolt.cli.command.ClasspathCommand");
        assertEquals(commandClass("config"), "com.zolt.cli.command.ConfigCommand");
        assertEquals(commandClass("version"), "com.zolt.cli.command.VersionCommand");
        assertEquals(commandClass("update"), "com.zolt.cli.command.UpdateCommand");
        assertEquals(commandClass("native-smoke"), "com.zolt.cli.command.NativeSmokeCommand");
        assertEquals(commandClass("release-verify"), "com.zolt.cli.command.ReleaseVerifyCommand");
        assertEquals(commandClass("release-archive"), "com.zolt.cli.command.ReleaseArchiveCommand");
        assertEquals(commandClass("publish"), "com.zolt.cli.command.PublishCommand");
        assertEquals(commandClass("native"), "com.zolt.cli.command.NativeCommand");
        assertEquals(commandClass("plan"), "com.zolt.cli.command.PlanCommand");
        assertEquals(commandClass("explain"), "com.zolt.cli.command.ExplainCommand");
    }

    private static String commandClass(String command) {
        return ZoltCli.newCommandLine()
                .getSubcommands()
                .get(command)
                .getCommandSpec()
                .userObject()
                .getClass()
                .getName();
    }

    private static void assertContainsInOrder(String text, String... expected) {
        int previousIndex = -1;
        for (String item : expected) {
            int index = text.indexOf(item);
            assertTrue(index > previousIndex, "Expected `" + item + "` after index " + previousIndex);
            previousIndex = index;
        }
    }

    private static int occurrences(String text, String fragment) {
        int count = 0;
        int start = 0;
        while (true) {
            int index = text.indexOf(fragment, start);
            if (index < 0) {
                return count;
            }
            count++;
            start = index + fragment.length();
        }
    }
}
