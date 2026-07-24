package sh.zolt.cli.command.dependency;

import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.net.CommandNetwork;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.maven.metadata.MetadataCache;
import sh.zolt.maven.metadata.RepositoryMetadataService;
import sh.zolt.maven.repository.RepositoryAccessException;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.update.OutdatedEngine;
import sh.zolt.update.OutdatedJsonRenderer;
import sh.zolt.update.OutdatedOptions;
import sh.zolt.update.OutdatedReport;
import sh.zolt.update.OutdatedScope;
import sh.zolt.update.OutdatedScopes;
import sh.zolt.update.OutdatedTextRenderer;
import sh.zolt.workspace.discovery.WorkspaceDiscoveryService;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "outdated",
        description = "Report available dependency, platform, and tooling version updates.")
public final class OutdatedCommand implements Runnable {
    enum Format {
        TEXT,
        JSON
    }

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--include-prereleases", description = "Include prerelease versions as update candidates.")
    private boolean includePrereleases;

    @Option(names = "--all", description = "Include surfaces that are already up to date.")
    private boolean all;

    @Option(names = "--offline", description = "Use only cached version listings; do not fetch metadata.")
    private boolean offline;

    @Parameters(
            arity = "0..*",
            paramLabel = "<SELECTOR>",
            description = "Restrict the report to a coordinate, version alias, or section token.")
    private List<String> selectors = new ArrayList<>();

    @Spec
    private CommandSpec spec;

    private final OutdatedEngine engine;
    private final OutdatedScopes scopes;
    private final WorkspaceDiscoveryService workspaceDiscovery;

    public OutdatedCommand() {
        this(defaultEngine(), new OutdatedScopes(), new WorkspaceDiscoveryService());
    }

    OutdatedCommand(OutdatedEngine engine, OutdatedScopes scopes, WorkspaceDiscoveryService workspaceDiscovery) {
        this.engine = engine;
        this.scopes = scopes;
        this.workspaceDiscovery = workspaceDiscovery;
    }

    @Override
    public void run() {
        try {
            List<OutdatedScope> reportScopes = resolveScopes(projectDirectory.path());
            OutdatedOptions options = new OutdatedOptions(includePrereleases, all, offline, selectors);
            OutdatedReport report = engine.report(reportScopes, options);
            String output = format == Format.JSON
                    ? new OutdatedJsonRenderer().render(report)
                    : new OutdatedTextRenderer().render(report);
            CommandOutput.printAndFlush(spec, output);
        } catch (LockfileReadException | ZoltConfigException | RepositoryAccessException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private List<OutdatedScope> resolveScopes(Path start) {
        Optional<Workspace> workspace = workspaceDiscovery.discover(start);
        if (workspace.isPresent() && sameDirectory(start, workspace.orElseThrow().root())) {
            return workspaceScopes(workspace.orElseThrow());
        }
        return List.of(scopes.fromDirectory(labelFor(start), start));
    }

    private List<OutdatedScope> workspaceScopes(Workspace workspace) {
        Optional<ZoltLockfile> lockfile = scopes.readLockfile(workspace.root().resolve("zolt.lock"));
        List<OutdatedScope> reportScopes = new ArrayList<>();
        for (WorkspaceMember member : workspace.members()) {
            reportScopes.add(new OutdatedScope(member.path(), member.config(), lockfile));
        }
        return reportScopes;
    }

    private static OutdatedEngine defaultEngine() {
        // Route metadata discovery through the composition root so the corporate proxy and CA bundle
        // from the user-global [network] config are honored, matching zolt resolve.
        RepositoryMetadataService discovery = new RepositoryMetadataService(
                CommandNetwork.repositoryClient(), new MetadataCache(LocalArtifactCache.defaultRoot()));
        return new OutdatedEngine(discovery);
    }

    private static boolean sameDirectory(Path left, Path right) {
        return left.toAbsolutePath().normalize().equals(right.toAbsolutePath().normalize());
    }

    private static String labelFor(Path start) {
        Path normalized = start.toAbsolutePath().normalize();
        Path name = normalized.getFileName();
        return name == null ? normalized.toString() : name.toString();
    }
}
