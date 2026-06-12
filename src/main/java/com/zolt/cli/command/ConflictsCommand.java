package com.zolt.cli.command;

import com.zolt.conflict.DependencyConflictFormatter;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfileReader;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "conflicts", description = "Show version conflicts and selected versions.")
public final class ConflictsCommand implements Runnable {
    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            String output = new DependencyConflictFormatter().format(
                    new ZoltLockfileReader().read(workingDirectory.resolve("zolt.lock")));
            CommandOutput.printAndFlush(spec, output);
        } catch (LockfileReadException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        }
    }
}
