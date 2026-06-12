package com.zolt.cli.command;

import picocli.CommandLine.Model.CommandSpec;

final class CommandOutput {
    private CommandOutput() {
    }

    static void printAndFlush(CommandSpec spec, String output) {
        spec.commandLine().getOut().print(output);
        spec.commandLine().getOut().flush();
    }
}
