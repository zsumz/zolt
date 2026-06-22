package com.zolt.cli;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Override
    public String render(Help help) {
        Map<String, Help> subcommands = help.subcommands();
        if (subcommands.isEmpty()) {
            return "";
        }

        StringBuilder output = new StringBuilder();
        Set<String> rendered = new LinkedHashSet<>();
        for (CommandGroup group : GROUPS) {
            appendGroup(output, group, subcommands, rendered);
        }
        appendUngroupedCommands(output, subcommands, rendered);
        return output.toString();
    }

    private static void appendGroup(
            StringBuilder output,
            CommandGroup group,
            Map<String, Help> subcommands,
            Set<String> rendered) {
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

        appendHeading(output, group.heading());
        for (String command : visibleCommands) {
            appendCommand(output, command, subcommands.get(command));
            rendered.add(command);
        }
        output.append(System.lineSeparator());
    }

    private static void appendUngroupedCommands(
            StringBuilder output,
            Map<String, Help> subcommands,
            Set<String> rendered) {
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

        appendHeading(output, "Other");
        for (String command : ungrouped) {
            appendCommand(output, command, subcommands.get(command));
        }
        output.append(System.lineSeparator());
    }

    private static void appendHeading(StringBuilder output, String heading) {
        output.append("  ")
                .append(heading)
                .append(System.lineSeparator());
    }

    private static void appendCommand(StringBuilder output, String command, Help commandHelp) {
        output.append("    ")
                .append(pad(command))
                .append(description(commandHelp))
                .append(System.lineSeparator());
    }

    private static String pad(String command) {
        if (command.length() >= COMMAND_COLUMN_WIDTH) {
            return command + "  ";
        }
        return command + " ".repeat(COMMAND_COLUMN_WIDTH - command.length());
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
