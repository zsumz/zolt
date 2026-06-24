package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;
import picocli.CommandLine.Help;
import picocli.CommandLine.IHelpSectionRenderer;

final class RootCommandListRenderer implements IHelpSectionRenderer {
    private static final int COMMAND_COLUMN_WIDTH = 20;
    private static final List<CommandGroup> GROUPS = List.of(
            new CommandGroup("Basics", List.of(
                    "help",
                    "init",
                    "version",
                    "config",
                    "doctor",
                    "update")),
            new CommandGroup("Dependencies", List.of(
                    "add",
                    "remove",
                    "platform",
                    "resolve",
                    "tree",
                    "why",
                    "policy",
                    "conflicts")),
            new CommandGroup("Build, Test, Run", List.of(
                    "build",
                    "run",
                    "test",
                    "integration-test",
                    "coverage",
                    "package",
                    "run-package",
                    "clean")),
            new CommandGroup("Insight and Tooling", List.of(
                    "check",
                    "plan",
                    "classpath",
                    "ide",
                    "explain",
                    "quarkus")),
            new CommandGroup("Native and Release", List.of(
                    "native",
                    "native-smoke",
                    "release-archive",
                    "release-verify",
                    "publish")),
            new CommandGroup("Self-Hosting", List.of(
                    "self-check",
                    "self-parity")));

    private final Supplier<ConsoleStyle> styles;

    RootCommandListRenderer(Supplier<ConsoleStyle> styles) {
        this.styles = styles;
    }

    @Override
    public String render(Help help) {
        Map<String, Help> subcommands = help.subcommands();
        if (subcommands.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        Set<String> rendered = new LinkedHashSet<>();
        ConsoleStyle style = styles.get();
        for (CommandGroup group : GROUPS) {
            appendGroup(output, group, subcommands, rendered, style);
        }
        appendUngroupedCommands(output, subcommands, rendered, style);
        return output.toString();
    }

    private static void appendGroup(
            StringBuilder output,
            CommandGroup group,
            Map<String, Help> subcommands,
            Set<String> rendered,
            ConsoleStyle style) {
        List<String> visibleCommands = new ArrayList<>();
        for (String command : group.commands()) {
            Help commandHelp = subcommands.get(command);
            if (commandHelp != null && !commandHelp.commandSpec().usageMessage().hidden()) {
                visibleCommands.add(command);
            }
        }
        if (visibleCommands.isEmpty()) {
            return;
        }

        appendHeading(output, group.heading(), style);
        for (String command : visibleCommands) {
            appendCommand(output, command, subcommands.get(command), style);
            rendered.add(command);
        }
        output.append(System.lineSeparator());
    }

    private static void appendUngroupedCommands(
            StringBuilder output,
            Map<String, Help> subcommands,
            Set<String> rendered,
            ConsoleStyle style) {
        List<String> ungrouped = new ArrayList<>();
        for (Map.Entry<String, Help> entry : subcommands.entrySet()) {
            if (!rendered.contains(entry.getKey())
                    && !entry.getValue().commandSpec().usageMessage().hidden()) {
                ungrouped.add(entry.getKey());
            }
        }
        if (ungrouped.isEmpty()) {
            return;
        }

        appendHeading(output, "Other", style);
        for (String command : ungrouped) {
            appendCommand(output, command, subcommands.get(command), style);
        }
        output.append(System.lineSeparator());
    }

    private static void appendHeading(StringBuilder output, String heading, ConsoleStyle style) {
        output.append("  ")
                .append(style.heading(heading))
                .append(System.lineSeparator());
    }

    private static void appendCommand(StringBuilder output, String command, Help commandHelp, ConsoleStyle style) {
        output.append("    ")
                .append(style.command(command))
                .append(padding(command))
                .append(description(commandHelp))
                .append(System.lineSeparator());
    }

    private static String padding(String command) {
        if (command.length() >= COMMAND_COLUMN_WIDTH) {
            return "  ";
        }
        return " ".repeat(COMMAND_COLUMN_WIDTH - command.length());
    }

    private static String description(Help commandHelp) {
        if ("help".equals(commandHelp.commandSpec().name())) {
            return "Display help for zolt or a command.";
        }

        StringBuilder description = new StringBuilder();
        for (String line : commandHelp.commandSpec().usageMessage().description()) {
            String trimmed = line.replace("%n", " ").trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (!description.isEmpty()) {
                description.append(' ');
            }
            description.append(trimmed);
        }
        return description.toString();
    }

    private record CommandGroup(String heading, List<String> commands) {
    }
}
