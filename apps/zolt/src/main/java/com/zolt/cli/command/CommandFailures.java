package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.PrintedUserException;
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
        return new PrintedUserException(spec.commandLine(), exception.getMessage());
    }

    static void printUser(CommandSpec spec, Exception exception) {
        printUser(spec, exception.getMessage());
    }

    static void printUser(CommandSpec spec, String displayMessage) {
        CommandErrorBlock block = CommandErrorBlock.from(displayMessage);
        CommandHumanOutput output = CommandHumanOutput.errors(spec);
        output.error(block.summary());
        if (!block.contextRows().isEmpty() || block.next().isPresent()) {
            output.blankLine();
        }
        for (CommandErrorBlock.ContextRow row : block.contextRows()) {
            output.context(row.label(), row.value());
        }
        block.next().ifPresent(output::next);
        spec.commandLine().getErr().flush();
    }
}
