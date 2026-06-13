package com.zolt.cli.command;

import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

final class CommandFailures {
    private CommandFailures() {
    }

    static CommandLine.ExecutionException user(CommandSpec spec, Exception exception) {
        return user(spec, exception.getMessage(), exception);
    }

    static CommandLine.ExecutionException user(CommandSpec spec, String displayMessage, Exception exception) {
        printUser(spec, displayMessage);
        return new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
    }

    static void printUser(CommandSpec spec, Exception exception) {
        printUser(spec, exception.getMessage());
    }

    static void printUser(CommandSpec spec, String displayMessage) {
        spec.commandLine().getErr().println("error: " + displayMessage);
        spec.commandLine().getErr().flush();
    }
}
