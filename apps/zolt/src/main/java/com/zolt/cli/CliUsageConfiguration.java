package com.zolt.cli;

import com.zolt.cli.console.ConsoleStyle;
import java.util.function.Supplier;
import picocli.CommandLine;
import picocli.CommandLine.Model.UsageMessageSpec;

final class CliUsageConfiguration {
    private CliUsageConfiguration() {
    }

    static void apply(CommandLine commandLine, Supplier<ConsoleStyle> styles) {
        commandLine.getCommandSpec()
                .usageMessage()
                .optionListHeading("Options:%n");
        commandLine.getCommandSpec()
                .usageMessage()
                .sectionMap()
                .put(UsageMessageSpec.SECTION_KEY_OPTION_LIST_HEADING,
                        help -> styles.get().heading("Options") + ":" + System.lineSeparator());
        commandLine.getCommandSpec()
                .usageMessage()
                .sectionMap()
                .put(UsageMessageSpec.SECTION_KEY_OPTION_LIST, new OptionGroupHelpRenderer(styles));
        commandLine.getCommandSpec()
                .usageMessage()
                .sectionMap()
                .put(UsageMessageSpec.SECTION_KEY_COMMAND_LIST, new RootCommandListRenderer(styles));
        commandLine.getSubcommands().values().forEach(subcommand -> apply(subcommand, styles));
    }
}
