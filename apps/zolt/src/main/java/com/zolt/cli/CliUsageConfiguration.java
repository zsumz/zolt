package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.List;
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
        usage.sectionKeys(List.of(
                UsageMessageSpec.SECTION_KEY_HEADER_HEADING,
                UsageMessageSpec.SECTION_KEY_HEADER,
                UsageMessageSpec.SECTION_KEY_DESCRIPTION_HEADING,
                UsageMessageSpec.SECTION_KEY_DESCRIPTION,
                UsageMessageSpec.SECTION_KEY_SYNOPSIS_HEADING,
                UsageMessageSpec.SECTION_KEY_SYNOPSIS,
                UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING,
                UsageMessageSpec.SECTION_KEY_AT_FILE_PARAMETER,
                UsageMessageSpec.SECTION_KEY_PARAMETER_LIST,
                UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING,
                UsageMessageSpec.SECTION_KEY_OPTION_LIST,
                UsageMessageSpec.SECTION_KEY_END_OF_OPTIONS,
                UsageMessageSpec.SECTION_KEY_COMMAND_LIST_HEADING,
                UsageMessageSpec.SECTION_KEY_COMMAND_LIST,
                UsageMessageSpec.SECTION_KEY_EXIT_CODE_LIST_HEADING,
                UsageMessageSpec.SECTION_KEY_EXIT_CODE_LIST,
                UsageMessageSpec.SECTION_KEY_FOOTER_HEADING,
                UsageMessageSpec.SECTION_KEY_FOOTER));
        usage.optionListHeading("Options:%n");
        usage.commandListHeading("Commands:%n");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_SYNOPSIS_HEADING,
                help -> styles.get().helpHeading("Usage") + ": ");
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_SYNOPSIS,
                help -> HelpSynopsisHighlighter.highlight(
                                HelpOptionValueSeparator.useSpaceForRequiredLongOptionValues(
                                        help.synopsis(USAGE_HEADING_WIDTH)),
                                styles.get())
                        + System.lineSeparator());
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_DESCRIPTION,
                CliUsageConfiguration::descriptionSection);
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_PARAMETER_LIST_HEADING,
                help -> parameterListHeading(help, styles));
        usage.sectionMap().put(
                UsageMessageSpec.SECTION_KEY_PARAMETER_LIST,
                help -> HelpParameterHighlighter.highlight(help.parameterList(), styles.get()));
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

    private static String parameterListHeading(Help help, Supplier<ConsoleStyle> styles) {
        if (help.commandSpec().positionalParameters().isEmpty()) {
            return "";
        }
        return styles.get().helpHeading("Arguments") + ":" + System.lineSeparator();
    }
}
