package com.zolt.cli.command;

import com.zolt.conflict.DependencyConflictFormatter;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.ZoltLockfileReader;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "conflicts", description = "Show version conflicts and selected versions.")
public final class ConflictsCommand implements Runnable {
    private final ZoltLockfileReader lockfileReader;
    private final DependencyConflictFormatter conflictFormatter;

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    public ConflictsCommand() {
        this(new ZoltLockfileReader(), new DependencyConflictFormatter());
    }

    ConflictsCommand(ZoltLockfileReader lockfileReader, DependencyConflictFormatter conflictFormatter) {
        this.lockfileReader = lockfileReader;
        this.conflictFormatter = conflictFormatter;
    }

    @Override
    public void run() {
        try {
            String output = conflictFormatter.format(lockfileReader.read(workingDirectory.resolve("zolt.lock")));
            CommandOutput.printAndFlush(spec, output);
        } catch (LockfileReadException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
