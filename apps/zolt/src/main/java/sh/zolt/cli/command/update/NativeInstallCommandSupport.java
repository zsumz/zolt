package sh.zolt.cli.command.update;

import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.channel.ReleaseDistributionUrlLayout;
import sh.zolt.release.update.NativeUpdateException;
import sh.zolt.release.update.NativeUpdateRequest;
import sh.zolt.release.update.NativeUpdateResult;
import sh.zolt.release.update.NativeUpdateService;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import picocli.CommandLine.Model.CommandSpec;

public final class NativeInstallCommandSupport {
    private NativeInstallCommandSupport() {
    }

    public static NativeUpdateResult update(
            NativeUpdateService nativeUpdateService,
            Path installRoot,
            Path currentExecutable,
            String channelUrl,
            String target,
            Path workDirectory) {
        ReleaseTarget releaseTarget = target == null ? ReleaseTarget.current() : ReleaseTarget.fromId(target);
        return nativeUpdateService.update(new NativeUpdateRequest(
                installRoot,
                effectiveCurrentExecutable(currentExecutable),
                URI.create(effectiveChannelUrl(installRoot, channelUrl)),
                releaseTarget,
                workDirectory));
    }

    public static Path effectiveCurrentExecutable(Path currentExecutable) {
        if (currentExecutable != null) {
            return currentExecutable;
        }
        return ProcessHandle.current()
                .info()
                .command()
                .map(Path::of)
                .orElseThrow(() -> new NativeUpdateException(
                        "Installer-managed native Zolt operations could not determine the current executable. Reinstall with the native installer."));
    }

    public static String effectiveChannelUrl(Path installRoot, String channelUrl) {
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

    public static void printUpdate(CommandSpec spec, NativeUpdateResult result) {
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
}
