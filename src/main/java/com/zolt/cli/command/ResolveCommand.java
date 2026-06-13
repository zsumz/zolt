package com.zolt.cli.command;

import com.zolt.cache.ArtifactCacheException;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.cli.ZoltCli;
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
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Mixin;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Spec;

@Command(name = "resolve", description = "Resolve dependencies, download artifacts, and write zolt.lock.")
public final class ResolveCommand implements Runnable {
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

    @Option(names = "--cwd", hidden = true)
    private Path workingDirectory = Path.of(".");

    @Option(names = "--cache-root", hidden = true)
    private Path cacheRoot = LocalArtifactCache.defaultRoot();

    @Option(names = "--maven-local-root", hidden = true)
    private Path mavenLocalRoot = Path.of(System.getProperty("user.home"), ".m2", "repository");

    @Mixin
    private ZoltCli.TimingOptions timingOptions = new ZoltCli.TimingOptions();

    @Spec
    private CommandSpec spec;

    @Override
    public void run() {
        TimingRecorder timings = CommandTimings.recorder(timingOptions);
        try {
            if (workspace) {
                if (!repositoryOverlays.isEmpty() || noLocalOverlays) {
                    throw new ResolveException(
                            "Repository overlay options are currently supported for single-project resolve only. "
                                    + "Run without --workspace or wait for workspace overlay policy support.");
                }
                ResolveResult result = timings.measure(
                        "resolve workspace",
                        () -> new WorkspaceResolveService().resolve(
                                workingDirectory,
                                cacheRoot,
                                locked,
                                offline),
                        ResolveCommand::resolveAttributes);
                CommandResolveOutput.print(spec, result, !locked);
                return;
            }
            ProjectConfig config = timings.measure(
                    "config read",
                    () -> new ZoltTomlParser().parse(workingDirectory.resolve("zolt.toml")));
            ResolveResult result = timings.measure(
                    "resolve graph",
                    () -> new ResolveService().resolve(
                            workingDirectory,
                            config,
                            cacheRoot,
                            locked,
                            resolveOptions()),
                    ResolveCommand::resolveAttributes);
            CommandResolveOutput.print(spec, result, !locked);
        } catch (ArtifactCacheException | ResolveException | WorkspaceConfigException | ZoltConfigException exception) {
            spec.commandLine().getErr().println("error: " + exception.getMessage());
            throw new CommandLine.ExecutionException(spec.commandLine(), exception.getMessage(), exception);
        } finally {
            CommandTimings.print(spec, "resolve", workingDirectory, timingOptions, timings);
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
        attributes.put("resolvedPackages", Integer.toString(result.resolvedCount()));
        attributes.put("downloadedArtifacts", Integer.toString(result.downloadCount()));
        attributes.put("conflicts", Integer.toString(result.conflictCount()));
        attributes.put("pomCacheHits", Integer.toString(result.metrics().pomCacheHits()));
        attributes.put("pomCacheMisses", Integer.toString(result.metrics().pomCacheMisses()));
        attributes.put("jarCacheHits", Integer.toString(result.metrics().jarCacheHits()));
        attributes.put("jarCacheMisses", Integer.toString(result.metrics().jarCacheMisses()));
        attributes.put("artifactCacheHits", Integer.toString(result.metrics().artifactCacheHits()));
        attributes.put("artifactCacheMisses", Integer.toString(result.metrics().artifactCacheMisses()));
        attributes.put("rawPomCacheHits", Integer.toString(result.metrics().rawPomCacheHits()));
        attributes.put("rawPomCacheMisses", Integer.toString(result.metrics().rawPomCacheMisses()));
        attributes.put("effectivePomCacheHits", Integer.toString(result.metrics().effectivePomCacheHits()));
        attributes.put("effectivePomCacheMisses", Integer.toString(result.metrics().effectivePomCacheMisses()));
        attributes.put("pomCacheHitMillis", Long.toString(result.metrics().pomCacheHitNanos() / 1_000_000L));
        attributes.put("pomDownloadMillis", Long.toString(result.metrics().pomDownloadNanos() / 1_000_000L));
        attributes.put("jarCacheHitMillis", Long.toString(result.metrics().jarCacheHitNanos() / 1_000_000L));
        attributes.put("jarDownloadMillis", Long.toString(result.metrics().jarDownloadNanos() / 1_000_000L));
        attributes.put("artifactCacheHitMillis", Long.toString(result.metrics().artifactCacheHitNanos() / 1_000_000L));
        attributes.put("artifactDownloadMillis", Long.toString(result.metrics().artifactDownloadNanos() / 1_000_000L));
        attributes.put("rawPomParseMillis", Long.toString(result.metrics().rawPomParseNanos() / 1_000_000L));
        attributes.put("effectivePomBuildMillis", Long.toString(result.metrics().effectivePomBuildNanos() / 1_000_000L));
        attributes.put("graphTraversalMillis", Long.toString(result.metrics().graphTraversalNanos() / 1_000_000L));
        attributes.put("versionSelectionMillis", Long.toString(result.metrics().versionSelectionNanos() / 1_000_000L));
        attributes.put("lockfileAssemblyMillis", Long.toString(result.metrics().lockfileAssemblyNanos() / 1_000_000L));
        attributes.put("lockfileWriteMillis", Long.toString(result.metrics().lockfileWriteNanos() / 1_000_000L));
        attributes.put(
                "lockfileVerificationMillis", Long.toString(result.metrics().lockfileVerificationNanos() / 1_000_000L));
        attributes.put("pomCacheHitNanos", Long.toString(result.metrics().pomCacheHitNanos()));
        attributes.put("pomDownloadNanos", Long.toString(result.metrics().pomDownloadNanos()));
        attributes.put("jarCacheHitNanos", Long.toString(result.metrics().jarCacheHitNanos()));
        attributes.put("jarDownloadNanos", Long.toString(result.metrics().jarDownloadNanos()));
        attributes.put("artifactCacheHitNanos", Long.toString(result.metrics().artifactCacheHitNanos()));
        attributes.put("artifactDownloadNanos", Long.toString(result.metrics().artifactDownloadNanos()));
        attributes.put("rawPomParseNanos", Long.toString(result.metrics().rawPomParseNanos()));
        attributes.put("effectivePomBuildNanos", Long.toString(result.metrics().effectivePomBuildNanos()));
        attributes.put("graphTraversalNanos", Long.toString(result.metrics().graphTraversalNanos()));
        attributes.put("versionSelectionNanos", Long.toString(result.metrics().versionSelectionNanos()));
        attributes.put("lockfileAssemblyNanos", Long.toString(result.metrics().lockfileAssemblyNanos()));
        attributes.put("lockfileWriteNanos", Long.toString(result.metrics().lockfileWriteNanos()));
        attributes.put("lockfileVerificationNanos", Long.toString(result.metrics().lockfileVerificationNanos()));
        return attributes;
    }
}
