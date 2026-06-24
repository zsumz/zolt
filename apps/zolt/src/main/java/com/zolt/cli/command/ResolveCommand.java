package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.CommandHumanOutput;
import com.zolt.cli.CommandProgress;
import com.zolt.cli.ZoltCli;
import com.zolt.cli.console.ProgressWriter;
import com.zolt.perf.TimingRecorder;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.RepositoryOverlay;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.ResolveOptions;
import com.zolt.resolve.ResolveResult;
import com.zolt.resolve.ResolveService;
import com.zolt.toml.ZoltConfigException;
import com.zolt.toml.ZoltTomlParser;
import com.zolt.workspace.WorkspaceConfigException;
import com.zolt.workspace.WorkspaceResolveService;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "resolve", description = "Resolve dependencies, download artifacts, and write zolt.lock.")
public final class ResolveCommand implements Runnable {
    private final ZoltTomlParser tomlParser;
    private final ResolveService resolveService;
    private final WorkspaceResolveService workspaceResolveService;

    @Option(names = "--locked", description = "Fail if zolt.lock would change.")
    private boolean locked;

    @Option(names = "--offline", description = "Use only artifacts already present in the local cache.")
    private boolean offline;

    @Option(
            names = "--repository-overlay",
            description = "Opt into a user-local repository overlay. Supported values: maven-local, local-maven.")
    private List<String> repositoryOverlays = List.of();

    @Option(
            names = "--no-local-overlays",
            description = "Reject lockfile packages resolved from local repository overlays.")
    private boolean noLocalOverlays;

    @Option(names = "--workspace", description = "Resolve the discovered workspace and write the root zolt.lock.")
    private boolean workspace;

    @Mixin
    private CommandProjectDirectory projectDirectory = new CommandProjectDirectory();

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Option(names = "--maven-local-root", hidden = true)
    private Path mavenLocalRoot = Path.of(System.getProperty("user.home"), ".m2", "repository");

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    public ResolveCommand() {
        this(CommandFrameworkServices.resolveCommandServices());
    }

    private ResolveCommand(CommandResolveServices services) {
        this(
                new ZoltTomlParser(),
                services.resolveService(),
                services.workspaceResolveService());
    }

    ResolveCommand(
            ZoltTomlParser tomlParser,
            ResolveService resolveService,
            WorkspaceResolveService workspaceResolveService) {
        this.tomlParser = tomlParser;
        this.resolveService = resolveService;
        this.workspaceResolveService = workspaceResolveService;
    }

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        ProgressWriter progress = CommandProgress.human(spec);
        Path projectRoot = projectDirectory.path();
        try {
            if (workspace) {
                if (!repositoryOverlays.isEmpty() || noLocalOverlays) {
                    throw new ResolveException(
                            "Repository overlay options are currently supported for single-project resolve only. "
                                    + "Run without --workspace or wait for workspace overlay policy support.");
                }
                progress.start("Resolving workspace dependencies");
                ResolveResult result = timings.measure(
                        "resolve workspace",
                        () -> workspaceResolveService.resolve(
                                projectRoot,
                                cacheRoot,
                                locked,
                                offline),
                        ResolveCommand::resolveAttributes);
                CommandResolveOutput.print(spec, result, !locked);
                CommandHumanOutput.of(spec).action("zolt build --workspace");
                progress.result("Resolved " + result.resolvedCount() + " packages");
                return;
            }
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> tomlParser.parse(projectRoot.resolve("zolt.toml")));
            progress.start("Resolving dependencies");
            ResolveResult result = timings.measure(
                    "resolve graph",
                    () -> resolveService.resolve(
                            projectRoot,
                            config,
                            cacheRoot,
                            locked,
                            resolveOptions()),
                    ResolveCommand::resolveAttributes);
            CommandResolveOutput.print(spec, result, !locked);
            CommandHumanOutput.of(spec).action("zolt build");
            progress.result("Resolved " + result.resolvedCount() + " packages");
        } catch (ArtifactCacheException | ResolveException | WorkspaceConfigException | ZoltConfigException exception) {
            throw CommandFailures.user(spec, exception);
        } finally {
            CommandTimings.print(spec, "resolve", projectRoot, timingOptions, timings);
        }
    }

    private ResolveOptions resolveOptions() {
        List<RepositoryOverlay> overlays = new ArrayList<>();
        for (String overlay : repositoryOverlays) {
            overlays.add(repositoryOverlay(overlay));
        }
        return new ResolveOptions(offline, overlays, noLocalOverlays);
    }

    private RepositoryOverlay repositoryOverlay(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "maven-local", "local-maven" -> RepositoryOverlay.mavenLocal(mavenLocalRoot);
            default -> throw new ResolveException(
                    "Unsupported repository overlay `"
                            + value
                            + "`. Supported overlays: maven-local.");
        };
    }

    private static Map<String, String> resolveAttributes(ResolveResult result) {
        LinkedHashMap<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CommandAttributeKeys.RESOLVED_PACKAGES, Integer.toString(result.resolvedCount()));
        attributes.put(CommandAttributeKeys.DOWNLOADED_ARTIFACTS, Integer.toString(result.downloadCount()));
        attributes.put(CommandAttributeKeys.CONFLICTS, Integer.toString(result.conflictCount()));
        attributes.put(CommandAttributeKeys.POM_CACHE_HITS, Integer.toString(result.metrics().pomCacheHits()));
        attributes.put(CommandAttributeKeys.POM_CACHE_MISSES, Integer.toString(result.metrics().pomCacheMisses()));
        attributes.put(CommandAttributeKeys.JAR_CACHE_HITS, Integer.toString(result.metrics().jarCacheHits()));
        attributes.put(CommandAttributeKeys.JAR_CACHE_MISSES, Integer.toString(result.metrics().jarCacheMisses()));
        attributes.put(CommandAttributeKeys.ARTIFACT_CACHE_HITS, Integer.toString(result.metrics().artifactCacheHits()));
        attributes.put(CommandAttributeKeys.ARTIFACT_CACHE_MISSES, Integer.toString(result.metrics().artifactCacheMisses()));
        attributes.put(CommandAttributeKeys.RAW_POM_CACHE_HITS, Integer.toString(result.metrics().rawPomCacheHits()));
        attributes.put(CommandAttributeKeys.RAW_POM_CACHE_MISSES, Integer.toString(result.metrics().rawPomCacheMisses()));
        attributes.put(CommandAttributeKeys.EFFECTIVE_POM_CACHE_HITS, Integer.toString(result.metrics().effectivePomCacheHits()));
        attributes.put(CommandAttributeKeys.EFFECTIVE_POM_CACHE_MISSES, Integer.toString(result.metrics().effectivePomCacheMisses()));
        attributes.put(CommandAttributeKeys.POM_CACHE_HIT_MILLIS, Long.toString(result.metrics().pomCacheHitNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.POM_DOWNLOAD_MILLIS, Long.toString(result.metrics().pomDownloadNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.JAR_CACHE_HIT_MILLIS, Long.toString(result.metrics().jarCacheHitNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.JAR_DOWNLOAD_MILLIS, Long.toString(result.metrics().jarDownloadNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.ARTIFACT_CACHE_HIT_MILLIS, Long.toString(result.metrics().artifactCacheHitNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.ARTIFACT_DOWNLOAD_MILLIS, Long.toString(result.metrics().artifactDownloadNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.RAW_POM_PARSE_MILLIS, Long.toString(result.metrics().rawPomParseNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.EFFECTIVE_POM_BUILD_MILLIS, Long.toString(result.metrics().effectivePomBuildNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.GRAPH_TRAVERSAL_MILLIS, Long.toString(result.metrics().graphTraversalNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.VERSION_SELECTION_MILLIS, Long.toString(result.metrics().versionSelectionNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.LOCKFILE_ASSEMBLY_MILLIS, Long.toString(result.metrics().lockfileAssemblyNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.LOCKFILE_WRITE_MILLIS, Long.toString(result.metrics().lockfileWriteNanos() / 1_000_000L));
        attributes.put(
                CommandAttributeKeys.LOCKFILE_VERIFICATION_MILLIS,
                Long.toString(result.metrics().lockfileVerificationNanos() / 1_000_000L));
        attributes.put(CommandAttributeKeys.POM_CACHE_HIT_NANOS, Long.toString(result.metrics().pomCacheHitNanos()));
        attributes.put(CommandAttributeKeys.POM_DOWNLOAD_NANOS, Long.toString(result.metrics().pomDownloadNanos()));
        attributes.put(CommandAttributeKeys.JAR_CACHE_HIT_NANOS, Long.toString(result.metrics().jarCacheHitNanos()));
        attributes.put(CommandAttributeKeys.JAR_DOWNLOAD_NANOS, Long.toString(result.metrics().jarDownloadNanos()));
        attributes.put(CommandAttributeKeys.ARTIFACT_CACHE_HIT_NANOS, Long.toString(result.metrics().artifactCacheHitNanos()));
        attributes.put(CommandAttributeKeys.ARTIFACT_DOWNLOAD_NANOS, Long.toString(result.metrics().artifactDownloadNanos()));
        attributes.put(CommandAttributeKeys.RAW_POM_PARSE_NANOS, Long.toString(result.metrics().rawPomParseNanos()));
        attributes.put(CommandAttributeKeys.EFFECTIVE_POM_BUILD_NANOS, Long.toString(result.metrics().effectivePomBuildNanos()));
        attributes.put(CommandAttributeKeys.GRAPH_TRAVERSAL_NANOS, Long.toString(result.metrics().graphTraversalNanos()));
        attributes.put(CommandAttributeKeys.VERSION_SELECTION_NANOS, Long.toString(result.metrics().versionSelectionNanos()));
        attributes.put(CommandAttributeKeys.LOCKFILE_ASSEMBLY_NANOS, Long.toString(result.metrics().lockfileAssemblyNanos()));
        attributes.put(CommandAttributeKeys.LOCKFILE_WRITE_NANOS, Long.toString(result.metrics().lockfileWriteNanos()));
        attributes.put(CommandAttributeKeys.LOCKFILE_VERIFICATION_NANOS, Long.toString(result.metrics().lockfileVerificationNanos()));
        return attributes;
    }
}
