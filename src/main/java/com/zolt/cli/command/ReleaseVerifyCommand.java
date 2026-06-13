package com.zolt.cli.command;

import com.zolt.project.ProjectConfig;
import com.zolt.release.ReleaseVerificationException;
import com.zolt.release.ReleaseVerificationResult;
import com.zolt.release.ReleaseVerificationService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import java.nio.file.Path;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(name = "release-verify", description = "Verify release archives by unpacking and smoking the binary.")
public final class ReleaseVerifyCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final ReleaseVerificationService releaseVerificationService;

    @Parameters(arity = "1..*", paramLabel = "ARCHIVE", description = "Release archive path to verify.")
    private List<Path> archives;

    @Option(names = "--work-dir", description = "Directory for unpacked verification work.")
    private Path workDirectory = Path.of("target/release-verify");

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Spec
    private CommandSpec spec;

    public ReleaseVerifyCommand() {
        this(new ZoltTomlParser(), new ReleaseVerificationService());
    }

    ReleaseVerifyCommand(ZoltTomlParser tomlParser, ReleaseVerificationService releaseVerificationService) {
        this.tomlParser = tomlParser;
        this.releaseVerificationService = releaseVerificationService;
    }

    @Override
    public void run() {
        try {
            ProjectConfig config = tomlParser.parse(workingDirectory.resolve("zolt.toml"));
            List<Path> resolvedArchives = archives.stream()
                    .map(path -> workingDirectory.resolve(path).normalize())
                    .toList();
            ReleaseVerificationResult result = releaseVerificationService.verify(
                    resolvedArchives,
                    workingDirectory.resolve(workDirectory).normalize(),
                    config.project().version());
            for (ReleaseVerificationResult.VerifiedArchive archive : result.archives()) {
                spec.commandLine().getOut().println("Verified release archive " + archive.archivePath());
                spec.commandLine().getOut().println("Unpacked to " + archive.unpackDirectory());
                spec.commandLine().getOut().println("Ran smoke binary " + archive.binaryPath());
            }
            spec.commandLine().getOut().println("Verified " + result.verifiedCount() + " release archives");
        } catch (ReleaseVerificationException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }
}
