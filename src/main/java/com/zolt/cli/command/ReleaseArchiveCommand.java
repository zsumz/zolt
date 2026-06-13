package com.zolt.cli.command;

import com.zolt.project.ProjectConfig;
import com.zolt.release.ReleaseArchiveException;
import com.zolt.release.ReleaseArchiveResult;
import com.zolt.release.ReleaseArchiveService;
import com.zolt.release.ReleaseTarget;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "release-archive", description = "Assemble a release archive from a native binary.")
public final class ReleaseArchiveCommand implements Runnable {
    @Option(names = "--target", description = "Release target. Supported: macos-arm64, macos-x64, linux-arm64, linux-x64, windows-x64.")
    private String target;

    @Option(names = "--binary", description = "Path to the native binary to archive.")
    private Path binary;

    @Option(names = "--output", description = "Directory for release archives.")
    private Path outputDirectory = Path.of("dist");

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        try {
            ProjectConfig config = new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml"));
            ReleaseTarget releaseTarget = target == null ? ReleaseTarget.current() : ReleaseTarget.fromId(target);
            Path nativeBinary = binary == null
                    ? defaultNativeBinary(config, releaseTarget)
                    : binary;
            ReleaseArchiveResult result = new ReleaseArchiveService().assemble(
                    workingDirectory,
                    config,
                    releaseTarget,
                    nativeBinary,
                    outputDirectory);
            spec.commandLine().getOut().println("Assembled " + result.target().id() + " release archive");
            spec.commandLine().getOut().println("Included " + result.fileCount() + " files under " + result.rootDirectory());
            spec.commandLine().getOut().println("Wrote archive to " + result.archivePath());
            spec.commandLine().getOut().println("Wrote checksum to " + result.checksumPath());
            spec.commandLine().getOut().println("Wrote manifest to " + result.manifestPath());
        } catch (ReleaseArchiveException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private static Path defaultNativeBinary(ProjectConfig config, ReleaseTarget target) {
        String imageName = config.nativeSettings()
                .withDefaultImageName(config.project().name())
                .imageName();
        String binaryName = target == ReleaseTarget.WINDOWS_X64 && !imageName.endsWith(".exe")
                ? imageName + ".exe"
                : imageName;
        return Path.of(config.nativeSettings().output()).resolve(binaryName);
    }
}
