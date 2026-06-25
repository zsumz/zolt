package com.zolt.cli.surface;

import static com.zolt.cli.surface.CliHelpSurfaceFixtures.ANSI_ESCAPE;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_BASICS_HEADING;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_CYAN_COLOR_OPTION;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_CYAN_HELP_OPTION;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_CYAN_INIT_COMMAND;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_CYAN_ZOLT_COMMAND;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_COMMANDS_HEADING;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_GREEN_OPTION;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.BOLD_USAGE_HEADING;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.CYAN_COLOR_METAVAR;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.CYAN_COMMAND_ARGUMENT;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.HELP_COMMAND_HINT;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.PLAIN_GREEN_OPTION;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.WARNING_COLOR;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.commandPaths;
import static com.zolt.cli.surface.CliHelpSurfaceFixtures.zoltCommandTypes;
import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.newCommandLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class CliHelpSurfaceTest {
    @Test
    void helpListsMvpCommands() {
        CommandResult result = execute("help");

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains(ANSI_ESCAPE));
        assertTrue(result.stdout().contains("The modern Java build toolkit."));
        assertTrue(result.stdout().contains("--color"));
        assertTrue(result.stdout().contains("--progress"));
        assertTrue(result.stdout().contains("--no-progress"));
        assertTrue(result.stdout().contains("--quiet"));
        assertTrue(result.stdout().contains("--list"));
        assertContainsInOrder(
                result.stdout(),
                "The modern Java build toolkit.",
                "Usage:",
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
                "    self-check",
                "Run zolt help <command> for more information on a command.");
        assertTrue(result.stdout().contains("help                Display help for zolt or a command."));
        assertFalse(result.stdout().contains("%n"));
    }

    @Test
    void helpSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(BOLD_USAGE_HEADING));
        assertTrue(result.stdout().contains(BOLD_CYAN_ZOLT_COMMAND));
        assertTrue(result.stdout().contains(CYAN_COMMAND_ARGUMENT));
        assertTrue(result.stdout().contains(BOLD_CYAN_COLOR_OPTION));
        assertTrue(result.stdout().contains(CYAN_COLOR_METAVAR));
        assertTrue(result.stdout().contains(BOLD_COMMANDS_HEADING));
        assertTrue(result.stdout().contains(BOLD_BASICS_HEADING));
        assertTrue(result.stdout().contains("    " + BOLD_CYAN_INIT_COMMAND
                + "                Create a new Zolt project."));
        assertTrue(result.stdout().contains("Run " + HELP_COMMAND_HINT + " for more information"));
        assertTrue(result.stdout().contains("Create a new Zolt project."));
        assertFalse(result.stdout().contains(BOLD_GREEN_OPTION));
        assertFalse(result.stdout().contains(WARNING_COLOR));
        assertFalse(result.stderr().contains(ANSI_ESCAPE));
    }

    @Test
    void colorNeverKeepsHelpAnsiFree() {
        CommandResult result = execute("--color=never", "help");

        assertEquals(0, result.exitCode());
        assertFalse(result.stdout().contains(ANSI_ESCAPE));
        assertTrue(result.stdout().contains("  Basics"));
        assertTrue(result.stdout().contains("    init                Create a new Zolt project."));
    }

    @Test
    void listShowsGroupedCommandInventoryWithoutUsage() {
        CommandResult result = execute("--list");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains(ANSI_ESCAPE));
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
                "    self-check",
                "Run zolt help <command> for more information on a command.");
    }

    @Test
    void listSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "--list");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(BOLD_COMMANDS_HEADING));
        assertTrue(result.stdout().contains(BOLD_BASICS_HEADING));
        assertTrue(result.stdout().contains(BOLD_CYAN_INIT_COMMAND));
        assertFalse(result.stderr().contains(ANSI_ESCAPE));
    }

    @Test
    void allRegisteredCommandsSupportDirectHelpOption() {
        for (List<String> path : commandPaths(newCommandLine())) {
            List<String> args = new ArrayList<>(path);
            args.add("--help");

            CommandResult result = execute(args.toArray(String[]::new));

            String commandName = path.isEmpty() ? "zolt" : "zolt " + String.join(" ", path);
            assertEquals(0, result.exitCode(), commandName + " --help should exit successfully");
            assertEquals("", result.stderr(), commandName + " --help should not write stderr");
            assertTrue(result.stdout().contains("Usage:"), commandName + " --help should print usage");
        }
    }

    @Test
    void allRegisteredCommandHelpRespectsColorNever() {
        for (List<String> path : commandPaths(newCommandLine())) {
            List<String> args = new ArrayList<>();
            args.add("--color=never");
            args.addAll(path);
            args.add("--help");

            CommandResult result = execute(args.toArray(String[]::new));

            String commandName = path.isEmpty() ? "zolt" : "zolt " + String.join(" ", path);
            assertEquals(0, result.exitCode(), commandName + " --help should exit successfully");
            assertEquals("", result.stderr(), commandName + " --help should not write stderr");
            assertFalse(result.stdout().contains(ANSI_ESCAPE), commandName + " --help should not color stdout");
            assertFalse(result.stderr().contains(ANSI_ESCAPE), commandName + " --help should not color stderr");
        }
    }

    @Test
    void allRegisteredCommandHelpUsesCargoStyleCyanOptionsWithoutWarningColor() {
        for (List<String> path : commandPaths(newCommandLine())) {
            List<String> args = new ArrayList<>();
            args.add("--color=always");
            args.addAll(path);
            args.add("--help");

            CommandResult result = execute(args.toArray(String[]::new));

            String commandName = path.isEmpty() ? "zolt" : "zolt " + String.join(" ", path);
            assertEquals(0, result.exitCode(), commandName + " --help should exit successfully");
            assertEquals("", result.stderr(), commandName + " --help should not write stderr");
            assertTrue(
                    result.stdout().contains(BOLD_USAGE_HEADING),
                    commandName + " --help should use a bold green usage heading");
            assertFalse(result.stdout().contains(WARNING_COLOR), commandName + " --help should not use warning color");
            assertFalse(result.stdout().contains(BOLD_GREEN_OPTION), commandName + " --help should not use green options");
            assertFalse(result.stdout().contains(PLAIN_GREEN_OPTION), commandName + " --help should not use plain green options");
            assertTrue(
                    result.stdout().contains(BOLD_CYAN_HELP_OPTION),
                    commandName + " --help should use bold cyan option tokens");
        }
    }

    @Test
    void directHelpIsConfiguredFromCompositionRoot() {
        for (Class<?> commandType : zoltCommandTypes(newCommandLine())) {
            CommandLine.Command annotation = commandType.getAnnotation(CommandLine.Command.class);
            assertFalse(
                    annotation.mixinStandardHelpOptions(),
                    commandType.getName() + " should rely on ZoltCli.configureUniversalHelp");
        }
    }

    @Test
    void leafCommandHelpDoesNotShowEmptyCommandList() {
        CommandResult result = execute("test", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains("Commands:"));
    }

    @Test
    void leafCommandForcedColorHelpDoesNotShowEmptyCommandList() {
        CommandResult result = execute("--color=always", "test", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertFalse(result.stdout().contains(BOLD_COMMANDS_HEADING));
    }

    private static void assertContainsInOrder(String text, String... expected) {
        int previousIndex = -1;
        for (String item : expected) {
            int index = text.indexOf(item);
            assertTrue(index > previousIndex, "Expected `" + item + "` after index " + previousIndex);
            previousIndex = index;
        }
    }
}
