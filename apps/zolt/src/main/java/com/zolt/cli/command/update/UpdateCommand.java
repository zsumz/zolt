package com.zolt.cli.command.update;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.command.CommandFailures;
import com.zolt.release.ReleaseTarget;
import com.zolt.release.archive.ReleaseArchiveException;
import com.zolt.release.channel.ReleaseChannelManifestException;
import com.zolt.release.channel.ReleaseDistributionUrlLayout;
import com.zolt.release.update.NativeUpdateException;
import com.zolt.release.update.NativeUpdateRequest;
import com.zolt.release.update.NativeUpdateResult;
import com.zolt.release.update.NativeUpdateService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "update", description = "Update the Zolt executable in place.", hidden = true)
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

    @Option(names = "--internal-enable-update", hidden = true)
    private boolean internalEnableUpdate;

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
            if (!internalEnableUpdate) {
                throw new NativeUpdateException(
                        "zolt update is not a public-alpha install path. Download the native archive, verify its checksum, extract it, and put its bin directory on PATH.");
            }
            ReleaseTarget releaseTarget = target == null ? ReleaseTarget.current() : ReleaseTarget.fromId(target);
            NativeUpdateResult result = nativeUpdateService.update(new NativeUpdateRequest(
                    installRoot,
                    effectiveCurrentExecutable(),
                    URI.create(effectiveChannelUrl()),
                    releaseTarget,
                    workDirectory));
            print(result);
            return 0;
        } catch (IllegalArgumentException
                | NativeUpdateException
                | ReleaseArchiveException
                | ReleaseChannelManifestException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void print(NativeUpdateResult result) {
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        if (result.updated()) {
            output.summary(
                    "Updated native Zolt to " + result.availableVersion(),
                    "from " + result.previousVersion(),
                    result.channel() + " channel",
                    result.target().id());
        } else {
            output.summary(
                    "Zolt is already current at " + result.previousVersion(),
                    result.channel() + " channel",
                    result.target().id());
        }
        output.pointer("wrote", result.executable().toString());
        if (result.updated()) {
            output.next("Run `zolt --version` to confirm the active native executable.");
        }
    }

    private Path effectiveCurrentExecutable() {
        if (currentExecutable != null) {
            return currentExecutable;
        }
        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .orElseThrow(() -> new NativeUpdateException(
                        "zolt update only supports installer-managed native Zolt layouts. Could not determine the current Zolt executable. Reinstall with the native installer."));
    }

    private String effectiveChannelUrl() {
        if (channelUrl != null && !channelUrl.isBlank()) {
            return channelUrl;
        }
        Path installedChannelUrl = installRoot.resolve("channel-url");
        if (Files.isRegularFile(installedChannelUrl)) {
            try {
                String value = Files.readString(installedChannelUrl, StandardCharsets.UTF_8).strip();
                if (!value.isBlank()) {
                    return value;
                }
            } catch (java.io.IOException exception) {
                // Fall back to the stable channel; update will still validate the install layout.
            }
        }
        return new ReleaseDistributionUrlLayout().channelManifestUrl("stable");
    }
}
