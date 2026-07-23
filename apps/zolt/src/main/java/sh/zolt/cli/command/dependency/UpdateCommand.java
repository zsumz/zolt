package sh.zolt.cli.command.dependency;

import sh.zolt.cache.ArtifactCacheException;
import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.command.CommandFailures;
import sh.zolt.cli.command.CommandFrameworkServices;
import sh.zolt.cli.command.CommandOutput;
import sh.zolt.cli.command.CommandProjectDirectory;
import sh.zolt.cli.command.CommandResolveOutput;
import sh.zolt.maven.metadata.MetadataCache;
import sh.zolt.maven.metadata.RepositoryMetadataService;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RepositoryAccessException;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.ResolveService;
import sh.zolt.toml.ZoltConfigException;
import sh.zolt.toml.ZoltTomlParser;
import sh.zolt.toml.ZoltTomlWriter;
import sh.zolt.update.UpdateCeiling;
import sh.zolt.update.UpdateEngine;
import sh.zolt.update.UpdateOptions;
import sh.zolt.update.UpdatePlan;
import sh.zolt.update.UpdatePlanJsonRenderer;
import sh.zolt.update.UpdatePlanTextRenderer;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "update",
        description = "Update dependency, platform, and version-alias versions in zolt.toml.")
public final class UpdateCommand implements Runnable {
    enum Format {
        TEXT,
        JSON
    }

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--format", description = "Output format: text or json.")
    private Format format = Format.TEXT;

    @Option(names = "--dry-run", description = "Print the planned edits without writing zolt.toml.")
    private boolean dryRun;

    @Option(names = "--no-resolve", description = "Update zolt.toml without refreshing zolt.lock.")
    private boolean noResolve;

    @Option(names = "--include-prereleases", description = "Allow prerelease versions as update targets.")
    private boolean includePrereleases;

    @Option(names = "--offline", description = "Use only cached version listings; do not fetch metadata.")
    private boolean offline;

    @Option(names = "--latest", description = "Allow updates across major versions.")
    private boolean latest;

    @Option(names = "--patch", description = "Cap updates at patch changes.")
    private boolean patch;

    @Option(names = "--minor", description = "Cap updates at minor changes within the current major.")
    private boolean minor;

    @Option(names = "--major", description = "Allow updates up to a new major version.")
    private boolean major;

    @Parameters(
            arity = "0..*",
            paramLabel = "<SELECTOR>",
            description = "Restrict the update to a coordinate, version alias, or section token.")
    private List<String> selectors = new ArrayList<>();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Spec
    private CommandSpec spec;

    private final ZoltTomlParser tomlParser;
    private final ZoltTomlWriter tomlWriter;
    private final ResolveService resolveService;
    private final UpdateEngine engine;

    public UpdateCommand() {
        this(
                CommandFrameworkServices.versionAliasCommandServices().tomlParser(),
                CommandFrameworkServices.versionAliasCommandServices().tomlWriter(),
                CommandFrameworkServices.versionAliasCommandServices().resolveService(),
                defaultEngine());
    }

    UpdateCommand(
            ZoltTomlParser tomlParser,
            ZoltTomlWriter tomlWriter,
            ResolveService resolveService,
            UpdateEngine engine) {
        this.tomlParser = tomlParser;
        this.tomlWriter = tomlWriter;
        this.resolveService = resolveService;
        this.engine = engine;
    }

    @Override
    public void run() {
        try {
            Path projectRoot = projectDirectory.path();
            Path configPath = projectRoot.resolve("zolt.toml");
            ProjectConfig config = tomlParser.parse(configPath);
            UpdateOptions options = new UpdateOptions(ceiling(), includePrereleases, offline, selectors);
            UpdatePlan plan = engine.plan(config, options);
            if (format == Format.JSON) {
                runJson(projectRoot, configPath, config, plan);
            } else {
                runText(projectRoot, configPath, config, plan);
            }
        } catch (ArtifactCacheException | RepositoryAccessException | ResolveException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        }
    }

    private void runText(Path projectRoot, Path configPath, ProjectConfig config, UpdatePlan plan) {
        UpdatePlanTextRenderer renderer = new UpdatePlanTextRenderer();
        if (dryRun || !plan.hasEdits()) {
            CommandOutput.printAndFlush(spec, renderer.render(plan, dryRun));
            return;
        }
        ProjectConfig updated = engine.apply(config, plan);
        CommandHumanOutput output = CommandHumanOutput.of(spec);
        DependencyEditCommentWarning.printIfNeeded(output, configPath);
        tomlWriter.write(configPath, updated);
        CommandOutput.printAndFlush(spec, renderer.render(plan, false));
        if (noResolve) {
            output.detail("Skipped resolve; run zolt resolve to refresh zolt.lock.");
            return;
        }
        CommandResolveOutput.print(spec, resolveService.resolve(projectRoot, updated, cacheRoot));
    }

    private void runJson(Path projectRoot, Path configPath, ProjectConfig config, UpdatePlan plan) {
        if (!dryRun && plan.hasEdits()) {
            ProjectConfig updated = engine.apply(config, plan);
            tomlWriter.write(configPath, updated);
            if (!noResolve) {
                resolveService.resolve(projectRoot, updated, cacheRoot);
            }
        }
        CommandOutput.printAndFlush(spec, new UpdatePlanJsonRenderer().render(plan, dryRun));
    }

    private UpdateCeiling ceiling() {
        if (latest) {
            return UpdateCeiling.LATEST;
        }
        if (major) {
            return UpdateCeiling.MAJOR;
        }
        if (minor) {
            return UpdateCeiling.MINOR;
        }
        if (patch) {
            return UpdateCeiling.PATCH;
        }
        return UpdateCeiling.DEFAULT;
    }

    private static UpdateEngine defaultEngine() {
        RepositoryMetadataService discovery = new RepositoryMetadataService(
                new MavenRepositoryClient(), new MetadataCache(LocalArtifactCache.defaultRoot()));
        return new UpdateEngine(discovery);
    }
}
