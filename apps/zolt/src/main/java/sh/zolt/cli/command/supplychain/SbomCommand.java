package sh.zolt.cli.command.supplychain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import sh.zolt.cli.ZoltCli;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.error.ActionableError;
import sh.zolt.error.ActionableException;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import sh.zolt.sbom.CycloneDxSbomWriter;
import sh.zolt.sbom.LockSbomAssembler;
import sh.zolt.sbom.SbomModel;
import sh.zolt.sbom.SbomScopeSelection;
import sh.zolt.sbom.SbomTimestamp;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;

@Command(name = "sbom", description = "Generate a CycloneDX software bill of materials from zolt.lock.")
public final class SbomCommand implements Runnable {
    enum Format {
        CYCLONEDX
    }

    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final LockSbomAssembler assembler;
    private final CycloneDxSbomWriter cycloneDxWriter;
    private final Clock clock;
    private final Map<String, String> environment;
    private final String toolVersion;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--format", paramLabel = "<FORMAT>", description = "Output format: cyclonedx.")
    private Format format = Format.CYCLONEDX;

    @Option(names = "--output", paramLabel = "<PATH>", description = "Write the SBOM to a file instead of stdout.")
    private Path output;

    @Option(names = "--include-provided", description = "Include provided-scope dependencies as optional.")
    private boolean includeProvided;

    @Option(names = "--include-dev", description = "Include dev-scope dependencies as optional.")
    private boolean includeDev;

    @Option(names = "--include-test", description = "Include test-scope dependencies as optional.")
    private boolean includeTest;

    @Option(
            names = "--include-tools",
            description = "Include annotation-processor and tooling dependencies as optional.")
    private boolean includeTools;

    @Option(
            names = "--timestamp",
            paramLabel = "<WHEN>",
            description = "Set metadata.timestamp: an ISO-8601 instant, or `now`. Omitted by default.")
    private String timestamp;

    @Spec
    private CommandSpec spec;

    public SbomCommand() {
        this(
                new ZoltTomlParser(),
                new ZoltLockfileReader(),
                new LockSbomAssembler(),
                new CycloneDxSbomWriter(),
                Clock.systemUTC(),
                System.getenv(),
                ZoltCli.version());
    }

    SbomCommand(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            LockSbomAssembler assembler,
            CycloneDxSbomWriter cycloneDxWriter,
            Clock clock,
            Map<String, String> environment,
            String toolVersion) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.assembler = assembler;
        this.cycloneDxWriter = cycloneDxWriter;
        this.clock = clock;
        this.environment = environment;
        this.toolVersion = toolVersion;
    }

    @Override
    public void run() {
        try {
            Path projectRoot = projectDirectory.path();
            Path lockfilePath = projectRoot.resolve("zolt.lock");
            if (!Files.isRegularFile(lockfilePath)) {
                throw new ActionableException(ActionableError.of(
                        "No zolt.lock found at " + lockfilePath + ".",
                        "Run `zolt resolve` to generate it, then re-run `zolt sbom`."));
            }
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
            Optional<String> resolvedTimestamp =
                    SbomTimestamp.resolve(Optional.ofNullable(timestamp), environment, clock);
            SbomScopeSelection selection =
                    new SbomScopeSelection(includeProvided, includeDev, includeTest, includeTools);
            SbomModel model = assembler.assemble(config, lockfile, selection, resolvedTimestamp, toolVersion);
            String document = cycloneDxWriter.write(model);
            if (output != null) {
                writeToFile(document);
            } else {
                CommandOutput.printAndFlush(spec, document);
            }
        } catch (LockfileReadException | ZoltConfigException | ActionableException exception) {
            throw CommandFailures.user(spec, exception);
        } catch (IOException exception) {
            throw CommandFailures.user(
                    spec,
                    "Could not write the SBOM to " + output + ".",
                    exception);
        }
    }

    private void writeToFile(String document) throws IOException {
        Path normalized = output.toAbsolutePath().normalize();
        if (normalized.getParent() != null) {
            Files.createDirectories(normalized.getParent());
        }
        Files.writeString(normalized, document, StandardCharsets.UTF_8);
    }
}
