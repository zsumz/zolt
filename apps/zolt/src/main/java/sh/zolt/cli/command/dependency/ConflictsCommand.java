package sh.zolt.cli.command.dependency;

import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.conflict.DependencyConflictFormatter;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Spec;

@Command(name = "conflicts", description = "Show version conflicts and selected versions.")
public final class ConflictsCommand implements Runnable {
    private final ZoltLockfileReader lockfileReader;
    private final DependencyConflictFormatter conflictFormatter;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

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
            String output = conflictFormatter.format(lockfileReader.read(projectDirectory.path().resolve("zolt.lock")));
            CommandOutput.printAndFlush(spec, output);
        } catch (LockfileReadException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
