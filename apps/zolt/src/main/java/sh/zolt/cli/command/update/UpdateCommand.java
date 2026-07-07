package sh.zolt.cli.command.update;

import sh.zolt.cli.command.CommandFailures;
import sh.zolt.release.archive.ReleaseArchiveException;
import sh.zolt.release.channel.ReleaseChannelManifestException;
import sh.zolt.release.update.NativeUpdateException;
import sh.zolt.release.update.NativeUpdateResult;
import sh.zolt.release.update.NativeUpdateService;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "update", description = "Update the installer-managed Zolt binary.")
public final class UpdateCommand implements Callable<Integer> {
    private final NativeUpdateService nativeUpdateService;

    @Option(names = "--install-root", hidden = true)
    private Path installRoot = Path.of(System.getProperty("user.home"), ".zolt");

    @Option(names = "--channel-url", hidden = true)
    private String channelUrl;

    @Option(names = "--target", hidden = true)
    private String target;

    @Option(names = "--current-executable", hidden = true)
    private Path currentExecutable;

    @Option(names = "--work-dir", hidden = true)
    private Path workDirectory;

    @Spec
    private CommandSpec spec;

    public UpdateCommand() {
        this(new NativeUpdateService());
    }

    UpdateCommand(NativeUpdateService nativeUpdateService) {
        this.nativeUpdateService = nativeUpdateService;
    }

    @Override
    public Integer call() {
        try {
            NativeUpdateResult result = NativeInstallCommandSupport.update(
                    nativeUpdateService,
                    installRoot,
                    currentExecutable,
                    channelUrl,
                    target,
                    workDirectory);
            NativeInstallCommandSupport.printUpdate(spec, result);
            return 0;
        } catch (IllegalArgumentException
                | NativeUpdateException
                | ReleaseArchiveException
                | ReleaseChannelManifestException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
