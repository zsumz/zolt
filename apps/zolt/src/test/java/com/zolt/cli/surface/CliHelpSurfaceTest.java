package com.zolt.cli.surface;

import static com.zolt.cli.CliTestSupport.execute;
import static com.zolt.cli.CliTestSupport.newCommandLine;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import picocli.CommandLine;

final class CliHelpSurfaceTest {
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
                "    self-check",
                "Run zolt help <command> for more information on a command.");
        assertTrue(result.stdout().contains("help                Display help for zolt or a command."));
        assertFalse(result.stdout().contains("%n"));
    }

    @Test
    void helpSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\u001B[32m--color"));
        assertTrue(result.stdout().contains("\u001B[1mCommands\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1mBasics\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36minit\u001B[0m"));
        assertTrue(result.stdout().contains("Run \u001B[36mzolt help <command>\u001B[0m for more information"));
        assertTrue(result.stdout().contains("Create a new Zolt project."));
        assertFalse(result.stdout().contains("\u001B[33m"));
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
                "    self-check",
                "Run zolt help <command> for more information on a command.");
    }

    @Test
    void listSupportsSparseSemanticColor() {
        CommandResult result = execute("--color=always", "--list");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("\u001B[1mCommands\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[1mBasics\u001B[0m"));
        assertTrue(result.stdout().contains("\u001B[36minit\u001B[0m"));
        assertFalse(result.stderr().contains("\u001B["));
    }

    @Test
    void nestedCommandHelpSupportsSparseSemanticCommandHeadingColor() {
        CommandResult result = execute("--color=always", "version", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("\u001B[1mCommands\u001B[0m:"));
        assertTrue(result.stdout().contains("\u001B[36mset\u001B[0m"));
        assertFalse(result.stdout().contains("  Dependencies"));
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
    void directHelpIsConfiguredFromCompositionRoot() {
        for (Class<?> commandType : zoltCommandTypes(newCommandLine())) {
            CommandLine.Command annotation = commandType.getAnnotation(CommandLine.Command.class);
            assertFalse(
                    annotation.mixinStandardHelpOptions(),
                    commandType.getName() + " should rely on ZoltCli.configureUniversalHelp");
        }
    }

    @Test
    void nestedCommandHelpUsesFlatCommandList() {
        CommandResult result = execute("version", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        assertTrue(result.stdout().contains("Commands:"));
        assertContainsInOrder(
                result.stdout(),
                "Commands:",
                "    set",
                "    remove");
        assertFalse(result.stdout().contains("  Dependencies"));
        assertFalse(result.stdout().contains("  Other"));
    }

    @Test
    void defaultOnlyHelpUsesStandardOptionOrder() {
        CommandResult result = execute("platform", "--help");

        assertEquals(0, result.exitCode());
        assertEquals("", result.stderr());
        String options = result.stdout().substring(result.stdout().indexOf("Options:"));
        assertContainsInOrder(
                options,
                "Options:",
                "--color",
                "--progress",
                "--no-progress",
                "--quiet",
                "--help",
                "--version");
    }

    @Test
    void initHelpShowsDirectoryOption() {
        CommandResult result = execute("init", "--help");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("--directory"));
        assertTrue(result.stdout().contains("Run as if Zolt was started in the given project"));
        assertTrue(result.stdout().contains("directory."));
        assertEquals("", result.stderr());
    }

    private static List<List<String>> commandPaths(CommandLine root) {
        List<List<String>> paths = new ArrayList<>();
        collectCommandPaths(root, List.of(), paths);
        return paths;
    }

    private static List<Class<?>> zoltCommandTypes(CommandLine root) {
        List<Class<?>> types = new ArrayList<>();
        collectZoltCommandTypes(root, types);
        return types;
    }

    private static void collectCommandPaths(CommandLine commandLine, List<String> prefix, List<List<String>> paths) {
        paths.add(prefix);
        for (Map.Entry<String, CommandLine> entry : commandLine.getSubcommands().entrySet()) {
            List<String> childPath = new ArrayList<>(prefix);
            childPath.add(entry.getKey());
            collectCommandPaths(entry.getValue(), childPath, paths);
        }
    }

    private static void collectZoltCommandTypes(CommandLine commandLine, List<Class<?>> types) {
        Object userObject = commandLine.getCommandSpec().userObject();
        if (userObject != null) {
            Class<?> type = userObject instanceof Class<?> commandClass ? commandClass : userObject.getClass();
            if (type.getName().startsWith("com.zolt.cli")) {
                types.add(type);
            }
        }
        commandLine.getSubcommands().values().forEach(subcommand -> collectZoltCommandTypes(subcommand, types));
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
