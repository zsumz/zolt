package com.zolt.cli;

import java.util.List;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "help", description = "Display help for zolt or a command.")
final class ZoltHelpCommand implements Callable<Integer> {
    @Parameters(arity = "0..*", paramLabel = "COMMAND", description = "Command path to show help for.")
    private List<String> commandPath = List.of();

    @Spec
    private CommandSpec spec;

    @Override
    public Integer call() {
        CommandLine root = rootCommandLine();
        CommandLine target = resolve(root);
        if (target == null) {
            return root.getCommandSpec().exitCodeOnInvalidInput();
        }
        target.usage(spec.commandLine().getOut());
        return root.getCommandSpec().exitCodeOnSuccess();
    }

    private CommandLine rootCommandLine() {
        CommandLine commandLine = spec.commandLine();
        while (commandLine.getParent() != null) {
            commandLine = commandLine.getParent();
        }
        return commandLine;
    }

    private CommandLine resolve(CommandLine root) {
        CommandLine target = root;
        for (String command : commandPath) {
            CommandLine subcommand = target.getSubcommands().get(command);
            if (subcommand == null) {
                spec.commandLine().getErr().println("Unknown subcommand '" + command + "'.");
                root.usage(spec.commandLine().getErr());
                return null;
            }
            target = subcommand;
        }
        return target;
    }
}
