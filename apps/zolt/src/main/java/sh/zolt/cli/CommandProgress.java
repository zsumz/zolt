package sh.zolt.cli;

import sh.zolt.cli.console.ProgressOutputContract;
import sh.zolt.cli.console.ProgressWriter;
import picocli.CommandLine;
import picocli.CommandLine.Model.CommandSpec;

public final class CommandProgress {
    private CommandProgress() {
    }

    public static ProgressWriter human(CommandSpec spec) {
        return writer(spec, ProgressOutputContract.HUMAN);
    }

    public static ProgressWriter parseable(CommandSpec spec) {
        return writer(spec, ProgressOutputContract.PARSEABLE);
    }

    private static ProgressWriter writer(CommandSpec spec, ProgressOutputContract outputContract) {
        CommandLine commandLine = spec.commandLine();
        ZoltCli root = root(commandLine);
        if (root == null) {
            return ProgressWriter.disabled(commandLine.getErr());
        }
        return new ProgressWriter(
                commandLine.getErr(),
                root.progressPolicy(),
                root.consoleStyle(),
                outputContract);
    }

    private static ZoltCli root(CommandLine commandLine) {
        CommandLine current = commandLine;
        while (current.getParent() != null) {
            current = current.getParent();
        }
        Object userObject = current.getCommandSpec().userObject();
        return userObject instanceof ZoltCli root ? root : null;
    }
}
