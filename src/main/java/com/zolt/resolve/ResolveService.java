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
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomParser;
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
import java.util.concurrent.ConcurrentHashMap;

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
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(DependencyMetadataSource source, DependencyPolicySettings dependencyPolicy);
    }

    private final class RepositoryContext
            implements DependencyMetadataSource,
                    ResolverMetricsSink,
                    LockfileAssemblyContext,
                    ArtifactLoadMetricsSink,
                    RawPomLoadMetricsSink,
                    EffectivePomLoadMetricsSink {
        private final ProjectConfig config;
        private final LocalArtifactCache cache;
        private final ResolveOptions options;
        private final RepositoryAccessPlanner repositoryAccessPlanner = new RepositoryAccessPlanner();
        private final RepositoryFetchCoordinator repositoryFetchCoordinator = new RepositoryFetchCoordinator();
        private final ArtifactMaterializer artifactMaterializer;
        private final ArtifactBatchMaterializer artifactBatchMaterializer = new ArtifactBatchMaterializer();
        private final PomMetadataPreloader pomMetadataPreloader = new PomMetadataPreloader();
        private final ProjectPlatformMetadataPlanner projectPlatformMetadataPlanner =
                new ProjectPlatformMetadataPlanner(coordinateParser);
        private final EffectivePomInheritanceBuilder effectivePomInheritanceBuilder =
                new EffectivePomInheritanceBuilder();
        private final ImportedBomDependencyManagementExpander importedBomDependencyManagementExpander =
                new ImportedBomDependencyManagementExpander();
        private final ParentPomChainLoader parentPomChainLoader = new ParentPomChainLoader();
        private final RawPomMetadataLoader rawPomMetadataLoader = new RawPomMetadataLoader(rawPomParser);
        private final EffectivePomMetadataLoader effectivePomMetadataLoader =
                new EffectivePomMetadataLoader(
                        parentPomChainLoader,
                        effectivePomInheritanceBuilder,
                        importedBomDependencyManagementExpander);
        private final Map<String, String> artifactSources = new ConcurrentHashMap<>();
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
            this.artifactMaterializer = new ArtifactMaterializer(cache, options, localOverlayMaterializer);
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            return effectivePomMetadataLoader.load(coordinate, List.of(), this::rawPom, this);
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
            return artifactMaterializer.getPom(coordinate, this::fetchPom, this);
        }

        CachedArtifact getJar(Coordinate coordinate) {
            return artifactMaterializer.getJar(coordinate, this::fetchJar, this);
        }

        CachedArtifact getArtifact(ArtifactDescriptor descriptor) {
            return artifactMaterializer.getArtifact(descriptor, this::fetchArtifact, this);
        }

        @Override
        public String sourceFor(CachedArtifact artifact) {
            return artifactSources.getOrDefault(artifact.repositoryPath(), "maven-central");
        }

        @Override
        public Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors) {
            return artifactBatchMaterializer.materialize(descriptors, cache.downloadConcurrency(), this::getArtifact);
        }

        @Override
        public synchronized void recordPomCacheHit(long elapsedNanos) {
            pomCacheHits++;
            pomCacheHitNanos += elapsedNanos;
        }

        @Override
        public synchronized void recordPomDownload(long elapsedNanos) {
            pomCacheMisses++;
            pomDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        @Override
        public synchronized void recordJarCacheHit(long elapsedNanos) {
            jarCacheHits++;
            jarCacheHitNanos += elapsedNanos;
        }

        @Override
        public synchronized void recordJarDownload(long elapsedNanos) {
            jarCacheMisses++;
            jarDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        @Override
        public synchronized void recordArtifactCacheHit(long elapsedNanos) {
            artifactCacheHits++;
            artifactCacheHitNanos += elapsedNanos;
        }

        @Override
        public synchronized void recordArtifactDownload(long elapsedNanos) {
            artifactCacheMisses++;
            artifactDownloadNanos += elapsedNanos;
            downloadCount++;
        }

        @Override
        public synchronized void recordRawPomCacheHit() {
            rawPomCacheHits++;
        }

        @Override
        public synchronized void recordRawPomCacheMiss() {
            rawPomCacheMisses++;
        }

        @Override
        public synchronized void recordRawPomParse(long elapsedNanos) {
            rawPomParseNanos += elapsedNanos;
        }

        @Override
        public synchronized void recordEffectivePomCacheHit() {
            effectivePomCacheHits++;
        }

        @Override
        public synchronized void recordEffectivePomCacheMiss() {
            effectivePomCacheMisses++;
        }

        @Override
        public synchronized void recordEffectivePomBuild(long elapsedNanos) {
            effectivePomBuildNanos += elapsedNanos;
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
                    this::load);
            return projectManagedVersions;
        }

        List<DependencyRequest> projectPlatformPropertiesRequests() {
            return projectPlatformMetadataPlanner.propertiesRequests(
                    config,
                    this::load);
        }

        private RawPom rawPom(Coordinate coordinate) {
            return rawPomMetadataLoader.load(coordinate, this::getPom, this);
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
            return repositoryFetchCoordinator.fetch(repositoryAccesses(), action::fetch);
        }

        private List<RepositoryAccess> repositoryAccesses() {
            return repositoryAccessPlanner.plan(config);
        }

    }
}
