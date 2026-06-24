package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Help;
import picocli.CommandLine.Model.UsageMessageSpec;

final class CliUsageConfiguration {
    private CliUsageConfiguration() {
    }

    static void apply(CommandLine commandLine, Supplier<ConsoleStyle> styles) {
        UsageMessageSpec usage = commandLine.getCommandSpec().usageMessage();
        usage.optionListHeading("Options:%n");
        usage.commandListHeading("Commands:%n");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_SYNOPSIS_HEADING,
                help -> styles.get().heading("Usage") + ": ");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING,
                help -> styles.get().heading("Options") + ":" + System.lineSeparator());
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
        return styles.get().heading("Commands") + ":" + System.lineSeparator();
    }
}
