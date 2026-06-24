package com.zolt.cli.command;

import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.project.ProjectConfig;
import com.zolt.release.ReleaseArchiveException;
import com.zolt.release.ReleaseArchiveResult;
import com.zolt.release.ReleaseArchiveService;
import com.zolt.release.ReleaseTarget;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(
        name = "release-archive",
        description = "Assemble a release archive from a native binary.")
public final class ReleaseArchiveCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final ReleaseArchiveService releaseArchiveService;

    @Option(names = "--target", description = "Release target. Supported: macos-arm64, macos-x64, linux-arm64, linux-x64, windows-x64.")
    private String target;

    @Option(names = "--binary", description = "Path to the native binary to archive.")
    private Path binary;

    @Option(names = "--output", description = "Directory for release archives.")
    private Path outputDirectory = Path.of("dist");

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Spec
    private CommandSpec spec;

    public ReleaseArchiveCommand() {
        this(new ZoltTomlParser(), new ReleaseArchiveService());
    }

    ReleaseArchiveCommand(ZoltTomlParser tomlParser, ReleaseArchiveService releaseArchiveService) {
        this.tomlParser = tomlParser;
        this.releaseArchiveService = releaseArchiveService;
    }

    @Override
    public void run() {
        ProgressWriter progress = CommandProgress.human(spec);
        Path projectRoot = projectDirectory.path();
        try {
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            ReleaseTarget releaseTarget = target == null ? ReleaseTarget.current() : ReleaseTarget.fromId(target);
            Path nativeBinary = binary == null
                    ? defaultNativeBinary(config, releaseTarget)
                    : binary;
            progress.start("Assembling release archive");
            ReleaseArchiveResult result = releaseArchiveService.assemble(
                    projectRoot,
                    config,
                    releaseTarget,
                    nativeBinary,
                    outputDirectory);
            CommandHumanOutput output = CommandHumanOutput.of(spec);
            output.success("Assembled " + result.target().id() + " release archive");
            output.detail("Included " + result.fileCount() + " files under " + result.rootDirectory());
            output.detail("Wrote archive to " + result.archivePath());
            output.detail("Wrote checksum to " + result.checksumPath());
            output.detail("Wrote manifest to " + result.manifestPath());
            progress.result("Assembled " + result.target().id() + " release archive");
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
