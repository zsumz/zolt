package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.lockfile.LockfileReadException;
import com.zolt.lockfile.LockfileFreshnessSummary;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileReader;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.MavenRepositoryClient;
import com.zolt.maven.PomInterpolationException;
import com.zolt.maven.PomPropertyInterpolator;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.maven.RawPomParser;
import com.zolt.maven.RepositoryMissingArtifactException;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicySettings;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

public final class ResolveService {
    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final ZoltLockfileWriter lockfileWriter;
    private final DependencyGraphResolver graphResolver;
    private final DependencyRequestPlanner dependencyRequestPlanner;
    private final LockfileAssembler lockfileAssembler;
    private final FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner;

    public ResolveService() {
        this(FrameworkDependencyRequestPlanner.none());
    }

    public ResolveService(FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this(new CoordinateParser(), frameworkDependencyRequestPlanner);
    }

    private ResolveService(
            CoordinateParser coordinateParser,
            FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this(
                coordinateParser,
                new MavenRepositoryClient(),
                new RawPomParser(),
                DependencyGraphTraverser::new,
                new VersionSelector(),
                new ZoltLockfileWriter(),
                defaultDependencyRequestPlanner(coordinateParser),
                new LockfileAssembler(coordinateParser),
                frameworkDependencyRequestPlanner);
    }

    ResolveService(
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser,
            DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector,
            ZoltLockfileWriter lockfileWriter,
            DependencyRequestPlanner dependencyRequestPlanner,
            LockfileAssembler lockfileAssembler,
            FrameworkDependencyRequestPlanner frameworkDependencyRequestPlanner) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.lockfileWriter = lockfileWriter;
        this.graphResolver = new DependencyGraphResolver(graphTraverserFactory, versionSelector);
        this.dependencyRequestPlanner = dependencyRequestPlanner == null
                ? defaultDependencyRequestPlanner(coordinateParser)
                : dependencyRequestPlanner;
        this.lockfileAssembler = lockfileAssembler == null
                ? new LockfileAssembler(coordinateParser)
                : lockfileAssembler;
        this.frameworkDependencyRequestPlanner = frameworkDependencyRequestPlanner == null
                ? FrameworkDependencyRequestPlanner.none()
                : frameworkDependencyRequestPlanner;
    }

    private static DependencyRequestPlanner defaultDependencyRequestPlanner(CoordinateParser coordinateParser) {
        return new DependencyRequestPlanner(
                coordinateParser,
                new ToolingDependencyContributor(coordinateParser));
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked) {
        return resolve(projectDirectory, config, cacheRoot, locked, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked, boolean offline) {
        return resolve(projectDirectory, config, cacheRoot, locked, ResolveOptions.offline(offline));
    }

    public ResolveResult resolve(
            Path projectDirectory,
            ProjectConfig config,
            Path cacheRoot,
            boolean locked,
            ResolveOptions options) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve` to create it, then retry `zolt resolve --locked`.");
        }
        if (locked && options.rejectLocalOverlays()) {
            rejectExistingLocalOverlayLockfile(lockfilePath);
        }
        if (!options.includeCoverageTooling() && existingLockfileHasCoverageTooling(lockfilePath)) {
            options = options.withCoverageTooling();
        }

        ResolveOutput output = resolveLockfile(config, cacheRoot, options);
        ZoltLockfile lockfile = output.lockfile();
        if (options.rejectLocalOverlays()) {
            rejectLocalOverlayLockfile(lockfile);
        }
        ResolveMetrics metrics = output.metrics();
        if (locked) {
            long started = System.nanoTime();
            verifyLocked(lockfilePath, lockfile);
            metrics = metrics.withLockfileVerificationNanos(elapsedSince(started));
        } else {
            long started = System.nanoTime();
            lockfileWriter.write(lockfilePath, lockfile);
            metrics = metrics.withLockfileWriteNanos(elapsedSince(started));
        }
        return new ResolveResult(
                lockfile.packages().size(),
                output.downloadCount(),
                lockfile.conflicts().size(),
                lockfilePath,
                metrics);
    }

    public ResolveResult resolveWithCoverageTooling(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false, ResolveOptions.defaults().withCoverageTooling());
    }

    public ResolveOutput resolveLockfile(ProjectConfig config, Path cacheRoot, boolean offline) {
        return resolveLockfile(config, cacheRoot, ResolveOptions.offline(offline));
    }

    public ResolveOutput resolveLockfile(ProjectConfig config, Path cacheRoot, ResolveOptions options) {
        RepositoryContext context = new RepositoryContext(config, new LocalArtifactCache(cacheRoot), options);
        Map<PackageId, String> managedVersions = context.projectManagedVersions();
        List<DependencyRequest> directRequests = dependencyRequestPlanner.plan(
                config,
                managedVersions,
                options.includeCoverageTooling());
        directRequests = relocateDirectRequests(context, directRequests);
        DependencyGraphResolution initial = graphResolver.resolve(
                context,
                context.config.dependencyPolicy(),
                directRequests,
                context);
        List<DependencyRequest> allRequests = new ArrayList<>(directRequests);
        allRequests.addAll(frameworkDependencyRequestPlanner.plan(frameworkDependencyRequestPlanRequest(
                context,
                initial.graph(),
                initial.selection(),
                directRequests,
                managedVersions)));
        DependencyGraphResolution resolved = allRequests.size() == directRequests.size()
                ? initial
                : graphResolver.resolve(
                        context,
                        context.config.dependencyPolicy(),
                        allRequests,
                        context);
        ZoltLockfile lockfile = lockfile(context, resolved.graph(), resolved.selection(), allRequests);
        return new ResolveOutput(lockfile, context.downloadCount(), context.metrics());
    }

    private void rejectExistingLocalOverlayLockfile(Path lockfilePath) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " while checking local overlay origins. Check that the file exists and is readable.",
                    exception);
        }
        if (existing.contains("source = \"local-overlay:")) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private void rejectLocalOverlayLockfile(ZoltLockfile lockfile) {
        boolean hasLocalOverlay = lockfile.packages().stream()
                .anyMatch(lockPackage -> localOverlaySource(lockPackage.source()));
        if (hasLocalOverlay) {
            throw new ResolveException(localOverlayRejectedMessage());
        }
    }

    private static boolean existingLockfileHasCoverageTooling(Path lockfilePath) {
        if (!Files.isRegularFile(lockfilePath)) {
            return false;
        }
        try {
            return new ZoltLockfileReader().read(lockfilePath).packages().stream()
                    .anyMatch(lockPackage -> lockPackage.scope() == DependencyScope.TOOL_COVERAGE);
        } catch (LockfileReadException exception) {
            return false;
        }
    }

    private static boolean localOverlaySource(String source) {
        return source != null && source.startsWith("local-overlay:");
    }

    private static String localOverlayRejectedMessage() {
        return "Local repository overlay artifacts are not allowed for this resolve. "
                + "Run `zolt resolve` without local overlays to refresh zolt.lock from configured repositories, "
                + "or remove --no-local-overlays for a local development-only resolve.";
    }

    private List<DependencyRequest> relocateDirectRequests(
            RepositoryContext context,
            List<DependencyRequest> directRequests) {
        DependencyRelocator relocator = new DependencyRelocator(context);
        return directRequests.stream()
                .map(relocator::relocate)
                .toList();
    }

    private void verifyLocked(Path lockfilePath, ZoltLockfile candidate) {
        String existing;
        try {
            existing = Files.readString(lockfilePath);
        } catch (IOException exception) {
            throw new ResolveException(
                    "Could not read zolt.lock at "
                            + lockfilePath
                            + " for locked resolve. Check that the file exists and is readable.",
                    exception);
        }

        String expected = lockfileWriter.write(candidate);
        if (!existing.equals(expected)) {
            String changedInputs = changedInputs(existing, candidate);
            throw new ResolveException(
                    "zolt.lock is out of date."
                            + changedInputs
                            + " Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.");
        }
    }

    private static String changedInputs(String existing, ZoltLockfile candidate) {
        try {
            return LockfileFreshnessSummary.changedInputs(new ZoltLockfileReader().read(existing), candidate);
        } catch (LockfileReadException exception) {
            return "";
        }
    }

    private ZoltLockfile lockfile(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        return lockfileAssembler.assemble(context, graph, selection, directRequests);
    }

    private FrameworkDependencyRequestPlanRequest frameworkDependencyRequestPlanRequest(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests,
            Map<PackageId, String> managedVersions) {
        Map<PackageId, List<SelectedDependencyScope>> selectedScopes = SelectedDependencyScopes.from(
                graph,
                selection,
                directRequests);
        List<FrameworkDependencyCandidate> candidates = selection.selectedNodes().stream()
                .sorted(Comparator.comparing(node -> node.packageId() + ":" + node.selectedVersion()))
                .map(node -> new FrameworkDependencyCandidate(
                        node.packageId(),
                        node.selectedVersion(),
                        selectedScopes.getOrDefault(node.packageId(), List.of()).stream()
                                .map(SelectedDependencyScope::scope)
                                .toList()))
                .toList();
        Map<PackageId, String> versions = new LinkedHashMap<>();
        for (PackageNode node : selection.selectedNodes()) {
            versions.put(node.packageId(), node.selectedVersion());
        }
        return new FrameworkDependencyRequestPlanRequest(
                context.config,
                candidates,
                versions,
                managedVersions,
                coordinate -> context.getJar(coordinate).cachePath(),
                context::projectPlatformPropertiesRequests);
    }


    private static long elapsedSince(long started) {
        return Math.max(0L, System.nanoTime() - started);
    }

    @FunctionalInterface
    private interface RepositoryFetchAction {
        com.zolt.maven.RepositoryArtifact fetch(RepositoryAccess access);
    }

    @FunctionalInterface
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(DependencyMetadataSource source, DependencyPolicySettings dependencyPolicy);
    }

    private final class RepositoryContext implements DependencyMetadataSource, ResolverMetricsSink, LockfileAssemblyContext {
        private final ProjectConfig config;
        private final LocalArtifactCache cache;
        private final ResolveOptions options;
        private final RepositoryAccessPlanner repositoryAccessPlanner = new RepositoryAccessPlanner();
        private final ArtifactBatchMaterializer artifactBatchMaterializer = new ArtifactBatchMaterializer();
        private final PomMetadataPreloader pomMetadataPreloader = new PomMetadataPreloader();
        private final ProjectPlatformMetadataPlanner projectPlatformMetadataPlanner =
                new ProjectPlatformMetadataPlanner(coordinateParser);
        private final Map<String, EffectiveRawPom> metadata = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<EffectiveRawPom>> metadataLoads = new ConcurrentHashMap<>();
        private final Map<String, RawPom> rawPoms = new ConcurrentHashMap<>();
        private final Map<String, CompletableFuture<RawPom>> rawPomLoads = new ConcurrentHashMap<>();
        private final Map<String, String> artifactSources = new ConcurrentHashMap<>();
        private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();
        private Map<PackageId, ManagedVersion> projectManagedVersions;
        private int downloadCount;
        private int pomCacheHits;
        private int pomCacheMisses;
        private int jarCacheHits;
        private int jarCacheMisses;
        private int artifactCacheHits;
        private int artifactCacheMisses;
        private int rawPomCacheHits;
        private int rawPomCacheMisses;
        private int effectivePomCacheHits;
        private int effectivePomCacheMisses;
        private long pomCacheHitNanos;
        private long pomDownloadNanos;
        private long jarCacheHitNanos;
        private long jarDownloadNanos;
        private long artifactCacheHitNanos;
        private long artifactDownloadNanos;
        private long rawPomParseNanos;
        private long effectivePomBuildNanos;
        private long graphTraversalNanos;
        private long versionSelectionNanos;
        private long lockfileAssemblyNanos;
        private final LocalOverlayMaterializer localOverlayMaterializer;

        RepositoryContext(ProjectConfig config, LocalArtifactCache cache, ResolveOptions options) {
            this.config = config;
            this.cache = cache;
            this.options = options;
            this.localOverlayMaterializer = new LocalOverlayMaterializer(cache, artifactSources);
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            return effectivePom(coordinate, List.of());
        }

        @Override
        public void preload(List<Coordinate> coordinates) {
            pomMetadataPreloader.preload(coordinates, cache.downloadConcurrency(), this::load);
        }

        @Override
        public ProjectConfig config() {
            return config;
        }

        @Override
        public CachedArtifact getPom(Coordinate coordinate) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayPom(coordinate);
            if (overlayArtifact.isPresent()) {
                recordPomCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact = cache.getCachedPom(coordinate);
                recordPomCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.pomPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchPom(coordinate, requested ->
                    fetchPom(requested));
            if (cached) {
                recordPomCacheHit(elapsedSince(started));
            } else {
                recordPomDownload(elapsedSince(started));
            }
            return artifact;
        }

        CachedArtifact getJar(Coordinate coordinate) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayArtifact(ArtifactDescriptor.jar(coordinate));
            if (overlayArtifact.isPresent()) {
                recordJarCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact = cache.getCachedJar(coordinate);
                recordJarCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.jarPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchJar(coordinate, requested ->
                    fetchJar(requested));
            if (cached) {
                recordJarCacheHit(elapsedSince(started));
            } else {
                recordJarDownload(elapsedSince(started));
            }
            return artifact;
        }

        CachedArtifact getArtifact(ArtifactDescriptor descriptor) {
            long started = System.nanoTime();
            Optional<CachedArtifact> overlayArtifact = materializeOverlayArtifact(descriptor);
            if (overlayArtifact.isPresent()) {
                recordArtifactCacheHit(elapsedSince(started));
                return overlayArtifact.orElseThrow();
            }
            if (options.offline()) {
                CachedArtifact artifact =
                        cache.getCachedArtifact(descriptor, descriptor.extension().toUpperCase(java.util.Locale.ROOT));
                recordArtifactCacheHit(elapsedSince(started));
                return artifact;
            }
            Path before = cache.artifactPath(descriptor);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchArtifact(descriptor, requested ->
                    fetchArtifact(descriptor));
            if (cached) {
                recordArtifactCacheHit(elapsedSince(started));
            } else {
                recordArtifactDownload(elapsedSince(started));
            }
            return artifact;
        }

        @Override
        public String sourceFor(CachedArtifact artifact) {
            return artifactSources.getOrDefault(artifact.repositoryPath(), "maven-central");
        }

        private Optional<CachedArtifact> materializeOverlayPom(Coordinate coordinate) {
            return localOverlayMaterializer.materializePom(options.repositoryOverlays(), coordinate);
        }

        private Optional<CachedArtifact> materializeOverlayArtifact(ArtifactDescriptor descriptor) {
            return localOverlayMaterializer.materializeArtifact(options.repositoryOverlays(), descriptor);
        }

        @Override
        public Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors) {
            return artifactBatchMaterializer.materialize(descriptors, cache.downloadConcurrency(), this::getArtifact);
        }

        private synchronized void recordPomCacheHit(long elapsedNanos) {
            pomCacheHits++;
            pomCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordPomDownload(long elapsedNanos) {
            pomCacheMisses++;
            pomDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordJarCacheHit(long elapsedNanos) {
            jarCacheHits++;
            jarCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordJarDownload(long elapsedNanos) {
            jarCacheMisses++;
            jarDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordArtifactCacheHit(long elapsedNanos) {
            artifactCacheHits++;
            artifactCacheHitNanos += elapsedNanos;
        }

        private synchronized void recordArtifactDownload(long elapsedNanos) {
            artifactCacheMisses++;
            artifactDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        private synchronized void recordRawPomCacheHit() {
            rawPomCacheHits++;
        }

        private synchronized void recordRawPomCacheMiss() {
            rawPomCacheMisses++;
        }

        private synchronized void recordRawPomParse(long elapsedNanos) {
            rawPomParseNanos += elapsedNanos;
        }

        private synchronized void recordEffectivePomCacheHit() {
            effectivePomCacheHits++;
        }

        private synchronized void recordEffectivePomCacheMiss() {
            effectivePomCacheMisses++;
        }

        private synchronized void recordEffectivePomBuild(long elapsedNanos) {
            effectivePomBuildNanos += elapsedNanos;
        }

        private EffectiveRawPom awaitEffectivePom(String key, CompletableFuture<EffectiveRawPom> future) {
            return awaitPomFuture("effective POM", key, future);
        }

        private RawPom awaitRawPom(String key, CompletableFuture<RawPom> future) {
            return awaitPomFuture("raw POM", key, future);
        }

        private <T> T awaitPomFuture(String kind, String key, CompletableFuture<T> future) {
            try {
                return future.get();
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new ResolveException(
                        "Interrupted while waiting for in-flight "
                                + kind
                                + " metadata "
                                + key
                                + ". Try again.",
                        exception);
            } catch (ExecutionException exception) {
                Throwable cause = exception.getCause();
                if (cause instanceof RuntimeException runtimeException) {
                    throw runtimeException;
                }
                if (cause instanceof Error error) {
                    throw error;
                }
                throw new ResolveException(
                        "Could not load in-flight "
                                + kind
                                + " metadata "
                                + key
                                + ". Try again.",
                        cause);
            }
        }

        int downloadCount() {
            return downloadCount;
        }

        synchronized ResolveMetrics metrics() {
            return new ResolveMetrics(
                    pomCacheHits,
                    pomCacheMisses,
                    jarCacheHits,
                    jarCacheMisses,
                    artifactCacheHits,
                    artifactCacheMisses,
                    rawPomCacheHits,
                    rawPomCacheMisses,
                    effectivePomCacheHits,
                    effectivePomCacheMisses,
                    pomCacheHitNanos,
                    pomDownloadNanos,
                    jarCacheHitNanos,
                    jarDownloadNanos,
                    artifactCacheHitNanos,
                    artifactDownloadNanos,
                    rawPomParseNanos,
                    effectivePomBuildNanos,
                    graphTraversalNanos,
                    versionSelectionNanos,
                    lockfileAssemblyNanos,
                    0L,
                    0L);
        }

        @Override
        public void addGraphTraversalNanos(long nanos) {
            graphTraversalNanos += nanos;
        }

        @Override
        public void addVersionSelectionNanos(long nanos) {
            versionSelectionNanos += nanos;
        }

        @Override
        public void addLockfileAssemblyNanos(long nanos) {
            lockfileAssemblyNanos += nanos;
        }

        Map<PackageId, String> projectManagedVersions() {
            Map<PackageId, String> versions = new LinkedHashMap<>();
            projectManagedVersionDetails().forEach((packageId, managedVersion) ->
                    versions.put(packageId, managedVersion.version()));
            return versions;
        }

        @Override
        public Map<PackageId, ManagedVersion> projectManagedVersionDetails() {
            if (projectManagedVersions != null) {
                return projectManagedVersions;
            }
            projectManagedVersions = projectPlatformMetadataPlanner.managedVersions(
                    config,
                    coordinate -> effectivePom(coordinate, List.of()));
            return projectManagedVersions;
        }

        List<DependencyRequest> projectPlatformPropertiesRequests() {
            return projectPlatformMetadataPlanner.propertiesRequests(
                    config,
                    coordinate -> effectivePom(coordinate, List.of()));
        }

        private EffectiveRawPom effectivePom(Coordinate coordinate, List<String> importStack) {
            String key = coordinate.toString();
            EffectiveRawPom cached = metadata.get(key);
            if (cached != null) {
                recordEffectivePomCacheHit();
                return cached;
            }
            if (importStack.contains(key)) {
                throw new ResolveException(
                        "Imported BOM cycle detected: "
                                + String.join(" -> ", importStack)
                                + " -> "
                                + key
                                + ". Remove one of the import-scoped dependencyManagement entries.");
            }

            CompletableFuture<EffectiveRawPom> pending = new CompletableFuture<>();
            CompletableFuture<EffectiveRawPom> existing = metadataLoads.putIfAbsent(key, pending);
            if (existing != null) {
                recordEffectivePomCacheHit();
                return awaitEffectivePom(key, existing);
            }
            recordEffectivePomCacheMiss();
            long started = System.nanoTime();
            try {
                RawPom rawPom = rawPom(coordinate);
                List<RawPom> parents = loadParents(rawPom);
                String groupId = rawPom.groupId().or(() -> nearestGroupId(parents)).orElse(coordinate.groupId());
                String version = rawPom.version()
                        .or(() -> nearestVersion(parents))
                        .orElse(coordinate.version().orElseThrow());
                Map<String, String> properties = inheritedProperties(rawPom, parents);
                List<RawPomDependency> dependencyManagement = inheritedDependencyManagement(rawPom, parents);
                EffectiveRawPom base =
                        new EffectiveRawPom(rawPom, parents, groupId, version, properties, dependencyManagement);
                List<String> nextStack = new ArrayList<>(importStack);
                nextStack.add(key);
                EffectiveRawPom effective = new EffectiveRawPom(
                        rawPom,
                        parents,
                        groupId,
                        version,
                        properties,
                        expandedDependencyManagement(base, nextStack));
                metadata.put(key, effective);
                pending.complete(effective);
                return effective;
            } catch (RuntimeException exception) {
                pending.completeExceptionally(exception);
                throw exception;
            } catch (Error error) {
                pending.completeExceptionally(error);
                throw error;
            } finally {
                metadataLoads.remove(key, pending);
                recordEffectivePomBuild(elapsedSince(started));
            }
        }

        private List<RawPom> loadParents(RawPom rawPom) {
            List<RawPom> nearestFirst = new ArrayList<>();
            RawPom current = rawPom;
            while (current.parent().isPresent()) {
                var parent = current.parent().orElseThrow();
                Coordinate parentCoordinate = new Coordinate(parent.groupId(), parent.artifactId(), Optional.of(parent.version()));
                RawPom parentPom = rawPom(parentCoordinate);
                nearestFirst.add(parentPom);
                current = parentPom;
            }
            List<RawPom> rootFirst = new ArrayList<>();
            for (int index = nearestFirst.size() - 1; index >= 0; index--) {
                rootFirst.add(nearestFirst.get(index));
            }
            return rootFirst;
        }

        private RawPom rawPom(Coordinate coordinate) {
            String key = coordinate.toString();
            RawPom cached = rawPoms.get(key);
            if (cached != null) {
                recordRawPomCacheHit();
                return cached;
            }
            CompletableFuture<RawPom> pending = new CompletableFuture<>();
            CompletableFuture<RawPom> existing = rawPomLoads.putIfAbsent(key, pending);
            if (existing != null) {
                recordRawPomCacheHit();
                return awaitRawPom(key, existing);
            }
            recordRawPomCacheMiss();
            try {
                CachedArtifact pomArtifact = getPom(coordinate);
                long started = System.nanoTime();
                RawPom parsed = rawPomParser.parse(pomArtifact.bytes());
                recordRawPomParse(elapsedSince(started));
                rawPoms.put(key, parsed);
                pending.complete(parsed);
                return parsed;
            } catch (RuntimeException exception) {
                pending.completeExceptionally(exception);
                throw exception;
            } catch (Error error) {
                pending.completeExceptionally(error);
                throw error;
            } finally {
                rawPomLoads.remove(key, pending);
            }
        }

        private com.zolt.maven.RepositoryArtifact fetchPom(Coordinate coordinate) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchPom(access.uri(), coordinate, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchJar(Coordinate coordinate) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchJar(access.uri(), coordinate, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchArtifact(ArtifactDescriptor descriptor) {
            return fetchFromRepositories(access ->
                    repositoryClient.fetchArtifact(access.uri(), descriptor, access.authentication()));
        }

        private com.zolt.maven.RepositoryArtifact fetchFromRepositories(RepositoryFetchAction action) {
            List<RepositoryAccess> repositories = repositoryAccesses();
            RepositoryMissingArtifactException lastMissing = null;
            for (RepositoryAccess repository : repositories) {
                try {
                    return action.fetch(repository);
                } catch (RepositoryMissingArtifactException exception) {
                    lastMissing = exception;
                }
            }
            if (lastMissing != null) {
                throw lastMissing;
            }
            throw new ResolveException(
                    "No repositories are configured in zolt.toml. Add [repositories] with at least one Maven-compatible repository URL.");
        }

        private List<RepositoryAccess> repositoryAccesses() {
            return repositoryAccessPlanner.plan(config);
        }

        private Optional<String> nearestGroupId(List<RawPom> parents) {
            for (int index = parents.size() - 1; index >= 0; index--) {
                if (parents.get(index).groupId().isPresent()) {
                    return parents.get(index).groupId();
                }
            }
            return Optional.empty();
        }

        private Optional<String> nearestVersion(List<RawPom> parents) {
            for (int index = parents.size() - 1; index >= 0; index--) {
                if (parents.get(index).version().isPresent()) {
                    return parents.get(index).version();
                }
            }
            return Optional.empty();
        }

        private Map<String, String> inheritedProperties(RawPom rawPom, List<RawPom> parents) {
            Map<String, String> properties = new LinkedHashMap<>();
            for (RawPom parent : parents) {
                properties.putAll(parent.properties());
            }
            properties.putAll(rawPom.properties());
            return properties;
        }

        private List<RawPomDependency> inheritedDependencyManagement(RawPom rawPom, List<RawPom> parents) {
            List<RawPomDependency> dependencies = new ArrayList<>();
            for (RawPom parent : parents) {
                dependencies.addAll(parent.dependencyManagement());
            }
            dependencies.addAll(rawPom.dependencyManagement());
            return dependencies;
        }

        private List<RawPomDependency> expandedDependencyManagement(EffectiveRawPom pom, List<String> importStack) {
            List<RawPomDependency> dependencies = new ArrayList<>();
            for (RawPomDependency dependency : pom.dependencyManagement()) {
                if (dependency.classifier().isPresent()) {
                    dependencies.add(dependency);
                    continue;
                }
                if (isImportedBom(dependency)) {
                    RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                    Coordinate bomCoordinate = new Coordinate(
                            interpolated.groupId(),
                            interpolated.artifactId(),
                            Optional.of(interpolated.version().orElseThrow(() -> new ResolveException(
                                    "Imported BOM "
                                            + interpolated.groupId()
                                            + ":"
                                            + interpolated.artifactId()
                                            + " in "
                                            + pom.groupId()
                                            + ":"
                                            + pom.rawPom().artifactId()
                                            + " is missing a version. Add a version before resolving."))));
                    EffectiveRawPom imported = effectivePom(bomCoordinate, importStack);
                    for (RawPomDependency importedDependency : imported.dependencyManagement()) {
                        if (importedDependency.classifier().isPresent()) {
                            dependencies.add(importedDependency);
                            continue;
                        }
                        interpolateImportedManagedDependency(importedDependency, imported)
                                .ifPresent(dependencies::add);
                    }
                } else {
                    dependencies.add(dependency);
                }
            }
            return dependencies;
        }

        private static boolean isImportedBom(RawPomDependency dependency) {
            return dependency.type().filter("pom"::equals).isPresent()
                    && dependency.scope().filter("import"::equals).isPresent();
        }

        private Optional<RawPomDependency> interpolateImportedManagedDependency(
                RawPomDependency dependency,
                EffectiveRawPom imported) {
            try {
                return Optional.of(interpolator.interpolateDependency(dependency, imported));
            } catch (PomInterpolationException exception) {
                if (dependency.scope().map(scope -> scope.equals("test") || scope.equals("provided")).orElse(false)) {
                    return Optional.empty();
                }
                throw exception;
            }
        }

    }
}
