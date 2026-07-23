package sh.zolt.resolve.materialization.session;

import sh.zolt.cache.CachedArtifact;
import sh.zolt.cache.LocalArtifactCache;
import sh.zolt.dependency.PackageId;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.CoordinateParser;
import sh.zolt.maven.repository.EffectiveRawPom;
import sh.zolt.maven.repository.MavenRepositoryClient;
import sh.zolt.maven.repository.RawPom;
import sh.zolt.maven.repository.RawPomParser;
import sh.zolt.maven.repository.RepositoryAccess;
import sh.zolt.maven.repository.RepositoryAccessPlanner;
import sh.zolt.maven.repository.RepositoryDownloadListener;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.ResolveOptions;
import sh.zolt.resolve.lockfile.assembly.LockfileAssemblyContext;
import sh.zolt.resolve.metrics.ResolveMetrics;
import sh.zolt.resolve.metrics.ResolverMetricsCollector;
import sh.zolt.resolve.metrics.ResolverMetricsSink;
import sh.zolt.resolve.metadata.DependencyMetadataSource;
import sh.zolt.resolve.metadata.platform.ManagedVersion;
import sh.zolt.resolve.metadata.platform.ProjectPlatformMetadataPlanner;
import sh.zolt.resolve.metadata.pom.EffectivePomInheritanceBuilder;
import sh.zolt.resolve.metadata.pom.EffectivePomMetadataLoader;
import sh.zolt.resolve.metadata.pom.ImportedBomDependencyManagementExpander;
import sh.zolt.resolve.metadata.pom.ParentPomChainLoader;
import sh.zolt.resolve.metadata.pom.PomMetadataPreloader;
import sh.zolt.resolve.metadata.pom.RawPomMetadataLoader;
import sh.zolt.resolve.request.DependencyRequest;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RepositorySession implements DependencyMetadataSource, ResolverMetricsSink, LockfileAssemblyContext {
    private final ProjectConfig config;
    private final LocalArtifactCache cache;
    private final RepositoryAccessPlanner repositoryAccessPlanner = new RepositoryAccessPlanner();
    private final RepositoryFetchCoordinator repositoryFetchCoordinator = new RepositoryFetchCoordinator();
    private final MavenRepositoryClient repositoryClient;
    private final RepositoryDownloadListener downloadProgressListener;
    private final ArtifactMaterializer artifactMaterializer;
    private final ArtifactBatchMaterializer artifactBatchMaterializer = new ArtifactBatchMaterializer();
    private final PomMetadataPreloader pomMetadataPreloader = new PomMetadataPreloader();
    private final ProjectPlatformMetadataPlanner projectPlatformMetadataPlanner;
    private final EffectivePomInheritanceBuilder effectivePomInheritanceBuilder = new EffectivePomInheritanceBuilder();
    private final ImportedBomDependencyManagementExpander importedBomDependencyManagementExpander =
            new ImportedBomDependencyManagementExpander();
    private final ParentPomChainLoader parentPomChainLoader = new ParentPomChainLoader();
    private final RawPomMetadataLoader rawPomMetadataLoader;
    private final EffectivePomMetadataLoader effectivePomMetadataLoader =
            new EffectivePomMetadataLoader(
                    parentPomChainLoader,
                    effectivePomInheritanceBuilder,
                    importedBomDependencyManagementExpander);
    private final Map<String, String> artifactSources = new ConcurrentHashMap<>();
    private final ResolverMetricsCollector metricsCollector = new ResolverMetricsCollector();
    private Map<PackageId, ManagedVersion> projectManagedVersions;

    public RepositorySession(
            ProjectConfig config,
            Path cacheRoot,
            ResolveOptions options,
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser) {
        this.config = config;
        this.cache = new LocalArtifactCache(cacheRoot);
        this.repositoryClient = repositoryClient;
        this.downloadProgressListener = options.artifactProgressListener()::onBytes;
        this.projectPlatformMetadataPlanner = new ProjectPlatformMetadataPlanner(coordinateParser);
        this.rawPomMetadataLoader = new RawPomMetadataLoader(rawPomParser);
        LocalOverlayMaterializer localOverlayMaterializer = new LocalOverlayMaterializer(cache, artifactSources);
        this.artifactMaterializer = new ArtifactMaterializer(cache, options, localOverlayMaterializer);
    }

    @Override
    public EffectiveRawPom load(Coordinate coordinate) {
        return effectivePomMetadataLoader.load(coordinate, List.of(), this::rawPom, metricsCollector);
    }

    @Override
    public void preload(List<Coordinate> coordinates) {
        pomMetadataPreloader.preload(
                coordinates,
                cache.downloadConcurrency(),
                cache.repositoryExecutionLane(),
                this::load);
    }

    @Override
    public ProjectConfig config() {
        return config;
    }

    @Override
    public CachedArtifact getPom(Coordinate coordinate) {
        return artifactMaterializer.getPom(coordinate, this::fetchPom, metricsCollector);
    }

    public CachedArtifact getJar(Coordinate coordinate) {
        return artifactMaterializer.getJar(coordinate, this::fetchJar, metricsCollector);
    }

    CachedArtifact getArtifact(ArtifactDescriptor descriptor) {
        return artifactMaterializer.getArtifact(descriptor, this::fetchArtifact, metricsCollector);
    }

    @Override
    public String sourceFor(CachedArtifact artifact) {
        return artifactSources.getOrDefault(artifact.repositoryPath(), "maven-central");
    }

    @Override
    public Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors) {
        return artifactBatchMaterializer.materialize(
                descriptors,
                cache.downloadConcurrency(),
                cache.repositoryExecutionLane(),
                this::getArtifact);
    }

    public int downloadCount() {
        return metricsCollector.downloadCount();
    }

    public ResolveMetrics metrics() {
        return metricsCollector.metrics();
    }

    @Override
    public void addGraphTraversalNanos(long nanos) {
        metricsCollector.addGraphTraversalNanos(nanos);
    }

    @Override
    public void addVersionSelectionNanos(long nanos) {
        metricsCollector.addVersionSelectionNanos(nanos);
    }

    @Override
    public void addLockfileAssemblyNanos(long nanos) {
        metricsCollector.addLockfileAssemblyNanos(nanos);
    }

    public Map<PackageId, String> projectManagedVersions() {
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

    public List<DependencyRequest> projectPlatformPropertiesRequests() {
        return projectPlatformMetadataPlanner.propertiesRequests(
                config,
                this::load);
    }

    private RawPom rawPom(Coordinate coordinate) {
        return rawPomMetadataLoader.load(coordinate, this::getPom, metricsCollector);
    }

    private sh.zolt.maven.repository.RepositoryArtifact fetchPom(Coordinate coordinate) {
        return fetchFromRepositories(access ->
                repositoryClient.fetchPom(access.uri(), coordinate, access.authentication(), downloadProgressListener));
    }

    private sh.zolt.maven.repository.RepositoryArtifact fetchJar(Coordinate coordinate) {
        return fetchFromRepositories(access ->
                repositoryClient.fetchJar(access.uri(), coordinate, access.authentication(), downloadProgressListener));
    }

    private sh.zolt.maven.repository.RepositoryArtifact fetchArtifact(ArtifactDescriptor descriptor) {
        return fetchFromRepositories(access ->
                repositoryClient.fetchArtifact(access.uri(), descriptor, access.authentication(), downloadProgressListener));
    }

    private sh.zolt.maven.repository.RepositoryArtifact fetchFromRepositories(RepositoryFetchAction action) {
        return repositoryFetchCoordinator.fetch(repositoryAccesses(), action::fetch);
    }

    private List<RepositoryAccess> repositoryAccesses() {
        return repositoryAccessPlanner.plan(config);
    }
}
