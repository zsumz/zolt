package sh.zolt.cli.command.supplychain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;
import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.cli.ZoltCli;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.error.ActionableError;
import sh.zolt.error.ActionableException;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.toml.ZoltLockfileReader;
import sh.zolt.project.ProjectConfig;
import sh.zolt.sbom.LicenseIndex;
import sh.zolt.sbom.LicenseNoticesWriter;
import sh.zolt.sbom.LicenseReport;
import sh.zolt.sbom.LicenseReportBuilder;
import sh.zolt.sbom.LicenseReportJsonWriter;
import sh.zolt.sbom.LicenseReportTextWriter;
import sh.zolt.sbom.LockSbomAssembler;
import sh.zolt.sbom.PomLicenseResolver;
import sh.zolt.sbom.SbomComponent;
import sh.zolt.sbom.SbomModel;
import sh.zolt.sbom.SbomScopeGroup;
import sh.zolt.sbom.SbomScopeSelection;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;

@Command(name = "licenses", description = "Report the licenses of resolved dependencies from cached POMs.")
public final class LicensesCommand implements Runnable {
    enum Format {
        TEXT,
        JSON
    }

    private final ZoltTomlParser tomlParser;
    private final ZoltLockfileReader lockfileReader;
    private final LockSbomAssembler assembler;
    private final String toolVersion;
    private final LicenseReportBuilder reportBuilder = new LicenseReportBuilder();
    private final LicenseReportTextWriter textWriter = new LicenseReportTextWriter();
    private final LicenseReportJsonWriter jsonWriter = new LicenseReportJsonWriter();
    private final LicenseNoticesWriter noticesWriter = new LicenseNoticesWriter();

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--format", paramLabel = "<FORMAT>", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(
            names = "--notices",
            paramLabel = "<PATH>",
            description = "Also write a deterministic THIRD_PARTY notices file to the given path.")
    private Path notices;

    @Option(names = "--include-provided", description = "Include provided-scope dependencies.")
    private boolean includeProvided;

    @Option(names = "--include-dev", description = "Include dev-scope dependencies.")
    private boolean includeDev;

    @Option(names = "--include-test", description = "Include test-scope dependencies.")
    private boolean includeTest;

    @Option(names = "--include-tools", description = "Include annotation-processor and tooling dependencies.")
    private boolean includeTools;

    @Option(
            names = "--offline",
            description = "Accepted for consistency; license resolution never uses the network.")
    private boolean offline;

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    public LicensesCommand() {
        this(new ZoltTomlParser(), new ZoltLockfileReader(), new LockSbomAssembler(), ZoltCli.version());
    }

    LicensesCommand(
            ZoltTomlParser tomlParser,
            ZoltLockfileReader lockfileReader,
            LockSbomAssembler assembler,
            String toolVersion) {
        this.tomlParser = tomlParser;
        this.lockfileReader = lockfileReader;
        this.assembler = assembler;
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
                        "Run `zolt resolve` to generate it, then re-run `zolt licenses`."));
            }
            ProjectConfig config = tomlParser.parse(projectRoot.resolve("zolt.toml"));
            ZoltLockfile lockfile = lockfileReader.read(lockfilePath);
            SbomScopeSelection selection =
                    new SbomScopeSelection(includeProvided, includeDev, includeTest, includeTools);
            LicenseIndex index = resolveLicenses(lockfile, selection);
            SbomModel model = assembler.assemble(config, lockfile, selection, Optional.empty(), toolVersion, index);
            List<SbomComponent> components = model.components();
            LicenseReport report = reportBuilder.build(components, index);

            String document = format == Format.JSON ? jsonWriter.write(report) : textWriter.write(report);
            CommandOutput.printAndFlush(spec, document);
            if (notices != null) {
                writeNotices(components, index);
            }
        } catch (LockfileReadException | ZoltConfigException | ActionableException exception) {
            throw CommandFailures.user(spec, exception);
        } catch (IOException exception) {
            throw CommandFailures.user(spec, "Could not write the notices file to " + notices + ".", exception);
        }
    }

    private LicenseIndex resolveLicenses(ZoltLockfile lockfile, SbomScopeSelection selection) {
        List<LockPackage> externalInScope = lockfile.packages().stream()
                .filter(lockPackage -> selection.includes(SbomScopeGroup.of(lockPackage.scope())))
                .filter(lockPackage -> lockPackage.pom().isPresent())
                .toList();
        return new PomLicenseResolver(cacheRoot).index(externalInScope);
    }

    private void writeNotices(List<SbomComponent> components, LicenseIndex index) throws IOException {
        Path normalized = notices.toAbsolutePath().normalize();
        if (normalized.getParent() != null) {
            Files.createDirectories(normalized.getParent());
        }
        Files.writeString(normalized, noticesWriter.write(components, index), StandardCharsets.UTF_8);
    }
}
