package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.release.NativeUpdateException;
import com.zolt.release.NativeUpdateRequest;
import com.zolt.release.NativeUpdateResult;
import com.zolt.release.NativeUpdateService;
import com.zolt.release.ReleaseArchiveException;
import com.zolt.release.ReleaseChannelManifestException;
import com.zolt.release.ReleaseDistributionUrlLayout;
import com.zolt.release.ReleaseTarget;
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
            output.success("Updated native Zolt to " + result.availableVersion());
        } else {
            output.success("Zolt is already current at " + result.previousVersion());
        }
        output.context("Channel", result.channel());
        output.context("Target", result.target().id());
        output.context("Current version", result.previousVersion());
        output.context("Available version", result.availableVersion());
        output.context("Executable", result.executable().toString());
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
