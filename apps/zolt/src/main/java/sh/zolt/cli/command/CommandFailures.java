package sh.zolt.cli.command;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.PrintedUserException;
import sh.zolt.error.ActionableError;
import sh.zolt.error.HasActionableError;
import java.util.Optional;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandFailures {
    private CommandFailures() {
    }

    public static CommandLine.ExecutionException user(CommandSpec spec, Exception exception) {
        Optional<ActionableError> carrier = actionableError(exception);
        if (carrier.isPresent()) {
            printUser(spec, carrier.get());
        } else {
            printUser(spec, exception.getMessage());
        }
        return new PrintedUserException(spec.commandLine(), exception.getMessage());
    }

    public static CommandLine.ExecutionException user(CommandSpec spec, ActionableError error) {
        printUser(spec, error);
        return new PrintedUserException(spec.commandLine(), error.message());
    }

    public static CommandLine.ExecutionException user(CommandSpec spec, String displayMessage, Exception exception) {
        printUser(spec, displayMessage);
        return new PrintedUserException(spec.commandLine(), exception.getMessage());
    }

    public static void printUser(CommandSpec spec, Exception exception) {
        Optional<ActionableError> carrier = actionableError(exception);
        if (carrier.isPresent()) {
            printUser(spec, carrier.get());
        } else {
            printUser(spec, exception.getMessage());
        }
    }

    public static void printUser(CommandSpec spec, ActionableError error) {
        render(spec, CommandErrorBlock.of(error.summary(), error.remediation()));
    }

    /**
     * Renders the structured carrier when {@code throwable} (or its cause chain) supplies an
     * {@link ActionableError}, returning {@code true} when it did. Lets the root execution-exception
     * handler render thrown {@link sh.zolt.error.ActionableException}s through the structured path
     * while leaving non-actionable errors on their existing flat-message path.
     */
    public static boolean printActionable(CommandSpec spec, Throwable throwable) {
        Optional<ActionableError> carrier = actionableError(throwable);
        carrier.ifPresent(error -> printUser(spec, error));
        return carrier.isPresent();
    }

    public static void printUser(CommandSpec spec, String displayMessage) {
        render(spec, CommandErrorBlock.from(displayMessage));
    }

    private static void render(CommandSpec spec, CommandErrorBlock block) {
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

    private static Optional<ActionableError> actionableError(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof HasActionableError carrier && carrier.actionableError() != null) {
                return Optional.of(carrier.actionableError());
            }
            current = current.getCause();
        }
        return Optional.empty();
    }
}
