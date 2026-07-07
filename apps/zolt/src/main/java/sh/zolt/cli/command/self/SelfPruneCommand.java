package sh.zolt.cli.command.self;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.release.update.NativeInstalledVersion;
import sh.zolt.release.update.NativeUpdateException;
import sh.zolt.release.update.NativeVersionPruneRequest;
import sh.zolt.release.update.NativeVersionPruneResult;
import sh.zolt.release.update.NativeVersionService;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "prune", description = "Prune old installed native Zolt versions.")
public final class SelfPruneCommand extends SelfCommand.NativeSelfOptions implements Callable<Integer> {
    private final NativeVersionService nativeVersionService;

    @Option(names = "--keep", required = true, description = "Installed version count to keep before preserving current and previous.")
    private int keep;

    @Option(names = "--dry-run", description = "Show versions that would be pruned without deleting them.")
    private boolean dryRun;

    @Spec
    private CommandSpec spec;

    public SelfPruneCommand() {
        this(new NativeVersionService());
    }

    SelfPruneCommand(NativeVersionService nativeVersionService) {
        this.nativeVersionService = nativeVersionService;
    }

    @Override
    public Integer call() {
        try {
            print(nativeVersionService.prune(new NativeVersionPruneRequest(
                    installRoot(),
                    currentExecutable(),
                    keep,
                    dryRun)));
            return 0;
        } catch (NativeUpdateException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void print(NativeVersionPruneResult result) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        String action = result.dryRun() ? "Would prune" : "Pruned";
        output.summary(
                action + " installed native Zolt versions",
                "removed " + result.prunedVersions().size(),
                "kept " + result.keptVersions().size(),
                "requested keep " + result.keep());
        result.previousVersion().ifPresent(version -> output.context("previous", version));
        for (NativeInstalledVersion version : result.prunedVersions()) {
            output.line("  - " + version.version());
        }
    }
}
