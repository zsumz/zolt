package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.UsageMessageSpec;

final class CliUsageConfiguration {
    private static final int USAGE_HEADING_WIDTH = "Usage: ".length();

    private CliUsageConfiguration() {
    }

    static void apply(CommandLine commandLine, Supplier<ConsoleStyle> styles) {
        UsageMessageSpec usage = commandLine.getCommandSpec().usageMessage();
        usage.optionListHeading("Options:%n");
        usage.commandListHeading("Commands:%n");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_SYNOPSIS_HEADING,
                help -> styles.get().helpHeading("Usage") + ": ");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_SYNOPSIS,
                help -> HelpSynopsisHighlighter.highlight(help.synopsis(USAGE_HEADING_WIDTH), styles.get())
                        + System.lineSeparator());
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_DESCRIPTION,
                CliUsageConfiguration::descriptionSection);
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING,
                help -> styles.get().helpHeading("Options") + ":" + System.lineSeparator());
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING,
                help -> commandListHeading(help, styles));
        usage.sectionMap().put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, new OptionGroupHelpRenderer(styles));
        usage.sectionMap().put(UsageMessageSpec.SECTION_KEY_COMMAND_LIST, new RootCommandListRenderer(styles));
        commandLine.getSubcommands().values().forEach(subcommand -> apply(subcommand, styles));
    }

    private static String commandListHeading(Help help, Supplier<ConsoleStyle> styles) {
        boolean hasVisibleSubcommands = help.subcommands().values().stream()
                .anyMatch(subcommand -> !subcommand.commandSpec().usageMessage().hidden());
        if (!hasVisibleSubcommands) {
            return "";
        }
        return System.lineSeparator() + styles.get().helpHeading("Commands") + ":" + System.lineSeparator();
    }

    private static String descriptionSection(Help help) {
        String description = help.description();
        if (description.isBlank()) {
            return "";
        }
        if (description.endsWith(System.lineSeparator())) {
            return description + System.lineSeparator();
        }
        return description + System.lineSeparator() + System.lineSeparator();
    }
}
