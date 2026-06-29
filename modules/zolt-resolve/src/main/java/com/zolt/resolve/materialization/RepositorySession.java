package com.zolt.resolve.materialization;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.dependency.PackageId;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.MavenRepositoryClient;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomParser;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.DependencyRequest;
import com.zolt.resolve.LockfileAssemblyContext;
import com.zolt.resolve.ResolveMetrics;
import com.zolt.resolve.ResolveOptions;
import com.zolt.resolve.ResolverMetricsCollector;
import com.zolt.resolve.ResolverMetricsSink;
import com.zolt.resolve.metadata.DependencyMetadataSource;
import com.zolt.resolve.metadata.EffectivePomInheritanceBuilder;
import com.zolt.resolve.metadata.EffectivePomMetadataLoader;
import com.zolt.resolve.metadata.ImportedBomDependencyManagementExpander;
import com.zolt.resolve.metadata.ManagedVersion;
import com.zolt.resolve.metadata.ParentPomChainLoader;
import com.zolt.resolve.metadata.PomMetadataPreloader;
import com.zolt.resolve.metadata.ProjectPlatformMetadataPlanner;
import com.zolt.resolve.metadata.RawPomMetadataLoader;
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
