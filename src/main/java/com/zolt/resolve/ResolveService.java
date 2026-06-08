package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.MavenRepositoryClient;
import com.zolt.maven.PomPropertyInterpolator;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.maven.RawPomParser;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import com.zolt.quarkus.QuarkusDeploymentArtifact;
import com.zolt.quarkus.QuarkusExtensionMetadata;
import com.zolt.quarkus.QuarkusExtensionMetadataReader;
import com.zolt.quarkus.QuarkusMetadataException;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipException;

public final class ResolveService {
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");
    private static final PackageId JUNIT_PLATFORM_CONSOLE_PACKAGE = new PackageId(
            "org.junit.platform",
            "junit-platform-console");
    private static final String JUNIT_PLATFORM_CONSOLE_VERSION = "1.11.4";

    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final DependencyGraphTraverserFactory graphTraverserFactory;
    private final VersionSelector versionSelector;
    private final ZoltLockfileWriter lockfileWriter;
    private final QuarkusExtensionMetadataReader quarkusMetadataReader;

    public ResolveService() {
        this(
                new CoordinateParser(),
                new MavenRepositoryClient(),
                new RawPomParser(),
                DependencyGraphTraverser::new,
                new VersionSelector(),
                new ZoltLockfileWriter(),
                new QuarkusExtensionMetadataReader());
    }

    ResolveService(
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser,
            DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector,
            ZoltLockfileWriter lockfileWriter,
            QuarkusExtensionMetadataReader quarkusMetadataReader) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.graphTraverserFactory = graphTraverserFactory;
        this.versionSelector = versionSelector;
        this.lockfileWriter = lockfileWriter;
        this.quarkusMetadataReader = quarkusMetadataReader;
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked) {
        return resolve(projectDirectory, config, cacheRoot, locked, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked, boolean offline) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve` to create it, then retry `zolt resolve --locked`.");
        }

        ResolveOutput output = resolveLockfile(config, cacheRoot, offline);
        ZoltLockfile lockfile = output.lockfile();
        if (locked) {
            verifyLocked(lockfilePath, lockfile);
        } else {
            lockfileWriter.write(lockfilePath, lockfile);
        }
        return new ResolveResult(
                lockfile.packages().size(),
                output.downloadCount(),
                lockfile.conflicts().size(),
                lockfilePath);
    }

    public ResolveOutput resolveLockfile(ProjectConfig config, Path cacheRoot, boolean offline) {
        RepositoryContext context = new RepositoryContext(config, new LocalArtifactCache(cacheRoot), offline);
        List<DependencyRequest> directRequests = directRequests(config, context.projectManagedVersions());
        ResolutionState initial = resolveGraph(context, directRequests);
        List<DependencyRequest> allRequests = new ArrayList<>(directRequests);
        allRequests.addAll(quarkusDeploymentRequests(context, initial.graph(), initial.selection(), directRequests));
        ResolutionState resolved = allRequests.size() == directRequests.size()
                ? initial
                : resolveGraph(context, allRequests);
        ZoltLockfile lockfile = lockfile(context, resolved.graph(), resolved.selection(), allRequests);
        return new ResolveOutput(lockfile, context.downloadCount());
    }

    private ResolutionState resolveGraph(RepositoryContext context, List<DependencyRequest> requests) {
        DependencyGraphTraverser traverser = graphTraverserFactory.create(context);
        ResolutionGraph graph = traverser.traverse(requests);
        return new ResolutionState(graph, versionSelector.select(requests, graph));
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
            throw new ResolveException(
                    "zolt.lock is out of date. Run `zolt resolve` to refresh it, then retry `zolt resolve --locked`.");
        }
    }

    private List<DependencyRequest> directRequests(ProjectConfig config, Map<PackageId, String> projectManagedVersions) {
        List<DependencyRequest> requests = new ArrayList<>();
        for (Map.Entry<String, String> dependency : config.apiDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.COMPILE,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedApiDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("api.dependencies", packageId, projectManagedVersions),
                    DependencyScope.COMPILE,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.dependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.COMPILE,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("dependencies", packageId, projectManagedVersions),
                    DependencyScope.COMPILE,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.runtimeDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.RUNTIME,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedRuntimeDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("runtime.dependencies", packageId, projectManagedVersions),
                    DependencyScope.RUNTIME,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.providedDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.PROVIDED,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedProvidedDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("provided.dependencies", packageId, projectManagedVersions),
                    DependencyScope.PROVIDED,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.devDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.DEV,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedDevDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("dev.dependencies", packageId, projectManagedVersions),
                    DependencyScope.DEV,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.testDependencies().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.TEST,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedTestDependencies()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("test.dependencies", packageId, projectManagedVersions),
                    DependencyScope.TEST,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.annotationProcessors().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.PROCESSOR,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedAnnotationProcessors()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("annotationProcessors", packageId, projectManagedVersions),
                    DependencyScope.PROCESSOR,
                    RequestOrigin.DIRECT));
        }
        for (Map.Entry<String, String> dependency : config.testAnnotationProcessors().entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(new DependencyRequest(
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    DependencyScope.TEST_PROCESSOR,
                    RequestOrigin.DIRECT));
        }
        for (String dependency : config.managedTestAnnotationProcessors()) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(new DependencyRequest(
                    packageId,
                    managedVersion("test.annotationProcessors", packageId, projectManagedVersions),
                    DependencyScope.TEST_PROCESSOR,
                    RequestOrigin.DIRECT));
        }
        addTestToolRequests(config, projectManagedVersions, requests);
        addPackageModeRequests(config, projectManagedVersions, requests);
        return requests;
    }

    private void addTestToolRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (!hasTestInputs(config)) {
            return;
        }
        boolean consoleAlreadyOnTestClasspath = requests.stream()
                .anyMatch(request -> request.packageId().groupId().equals("org.junit.platform")
                        && request.packageId().artifactId().startsWith("junit-platform-console")
                        && request.scope().entersTestClasspath());
        if (consoleAlreadyOnTestClasspath) {
            return;
        }
        String version = projectManagedVersions.getOrDefault(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                JUNIT_PLATFORM_CONSOLE_VERSION);
        if (version == null || version.isBlank()) {
            version = JUNIT_PLATFORM_CONSOLE_VERSION;
        }
        requests.add(new DependencyRequest(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                version,
                DependencyScope.TEST,
                RequestOrigin.TRANSITIVE));
    }

    private static boolean hasTestInputs(ProjectConfig config) {
        return !config.testDependencies().isEmpty()
                || !config.managedTestDependencies().isEmpty()
                || !config.workspaceTestDependencies().isEmpty()
                || !config.testAnnotationProcessors().isEmpty()
                || !config.managedTestAnnotationProcessors().isEmpty();
    }

    private void addPackageModeRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (config.packageSettings().mode() != PackageMode.SPRING_BOOT) {
            return;
        }
        boolean loaderAlreadyOnMainRuntimeClasspath = requests.stream()
                .anyMatch(request -> request.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)
                        && request.scope().entersMainRuntimeClasspath());
        if (loaderAlreadyOnMainRuntimeClasspath) {
            return;
        }
        String version = projectManagedVersions.get(SPRING_BOOT_LOADER_PACKAGE);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Spring Boot package mode requires package tool artifact `org.springframework.boot:spring-boot-loader`, "
                            + "but no declared [platforms] entry manages its version. Add the Spring Boot platform to [platforms] "
                            + "or declare `org.springframework.boot:spring-boot-loader` with an explicit version, then run `zolt resolve`.");
        }
        requests.add(new DependencyRequest(
                SPRING_BOOT_LOADER_PACKAGE,
                version,
                DependencyScope.RUNTIME,
                RequestOrigin.TRANSITIVE));
    }

    private static String managedVersion(
            String section,
            PackageId packageId,
            Map<PackageId, String> projectManagedVersions) {
        String version = projectManagedVersions.get(packageId);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Dependency "
                            + packageId
                            + " in ["
                            + section
                            + "] uses a platform-managed version, but no declared [platforms] entry manages it. Add a version or add a platform that manages this dependency.");
        }
        return version;
    }

    private ZoltLockfile lockfile(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedScope>> selectedScopes = selectedScopes(graph, selection, directRequests);
        List<LockPackage> packages = selection.selectedNodes().stream()
                .flatMap(node -> selectedScopes
                        .getOrDefault(node.packageId(), List.of(new SelectedScope(DependencyScope.COMPILE, false)))
                        .stream()
                        .map(scope -> lockPackage(context, node, scope, graph)))
                .toList();
        List<LockConflict> conflicts = selection.conflicts().stream()
                .map(conflict -> new LockConflict(
                        conflict.packageId(),
                        conflict.selectedVersion(),
                        conflict.requests().stream().map(DependencyRequest::requestedVersion).toList(),
                        conflict.selectionReason()))
                .toList();
        return new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, packages, conflicts);
    }

    private List<DependencyRequest> quarkusDeploymentRequests(
            RepositoryContext context,
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedScope>> selectedScopes = selectedScopes(graph, selection, directRequests);
        Map<String, DependencyRequest> requests = new LinkedHashMap<>();
        selection.selectedNodes().stream()
                .sorted(Comparator.comparing(node -> node.packageId() + ":" + node.selectedVersion()))
                .forEach(node -> {
                    boolean runtimeExtensionCandidate = selectedScopes
                            .getOrDefault(node.packageId(), List.of())
                            .stream()
                            .map(SelectedScope::scope)
                            .anyMatch(scope -> scope.entersMainRuntimeClasspath() && scope.packagedByDefault());
                    if (!runtimeExtensionCandidate) {
                        return;
                    }
                    Coordinate coordinate = new Coordinate(
                            node.packageId().groupId(),
                            node.packageId().artifactId(),
                            Optional.of(node.selectedVersion()));
                    CachedArtifact jar = context.getJar(coordinate);
                    Optional<QuarkusExtensionMetadata> metadata = quarkusMetadata(jar.cachePath());
                    metadata.ifPresent(quarkusMetadata -> {
                        QuarkusDeploymentArtifact artifact = quarkusMetadata.deploymentArtifact();
                        if (!"jar".equals(artifact.type())) {
                            throw new ResolveException(
                                    "Quarkus extension "
                                            + coordinate
                                            + " declares deployment artifact "
                                            + artifact
                                            + ", but Zolt currently supports only jar deployment artifacts. "
                                            + "Remove that extension or wait for type-aware artifact resolution.");
                        }
                        DependencyRequest request = new DependencyRequest(
                                new PackageId(artifact.groupId(), artifact.artifactId()),
                                artifact.version(),
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                RequestOrigin.TRANSITIVE,
                                Optional.of(ArtifactDescriptor.jar(
                                        new Coordinate(
                                                artifact.groupId(),
                                                artifact.artifactId(),
                                                Optional.of(artifact.version())),
                                        artifact.classifier())));
                        requests.put(request.packageId() + ":" + request.requestedVersion() + ":" + request.scope(), request);
                    });
                });
        return List.copyOf(requests.values());
    }

    private Optional<QuarkusExtensionMetadata> quarkusMetadata(Path jarPath) {
        try {
            return quarkusMetadataReader.readIfPresent(jarPath);
        } catch (QuarkusMetadataException exception) {
            if (exception.getCause() instanceof ZipException) {
                return Optional.empty();
            }
            throw exception;
        }
    }

    private Map<PackageId, List<SelectedScope>> selectedScopes(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        Map<PackageId, List<SelectedScope>> requests = new LinkedHashMap<>();
        List<DependencyRequest> allRequests = new ArrayList<>();
        allRequests.addAll(directRequests);
        allRequests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        for (PackageNode node : selection.selectedNodes()) {
            Map<DependencyScope, SelectedScope> scopesByScope = new LinkedHashMap<>();
            allRequests.stream()
                    .filter(request -> request.packageId().equals(node.packageId()))
                    .forEach(request -> scopesByScope.merge(
                            request.scope(),
                            new SelectedScope(request.scope(), request.direct(), request.artifactDescriptor()),
                            SelectedScope::merge));
            List<SelectedScope> scopes = scopesByScope.values()
                    .stream()
                    .sorted(Comparator.comparing(selectedScope -> selectedScope.scope().lockfileName()))
                    .toList();
            if (!scopes.isEmpty()) {
                requests.put(node.packageId(), scopes);
            }
        }
        return requests;
    }

    private LockPackage lockPackage(
            RepositoryContext context,
            PackageNode node,
            SelectedScope selectedScope,
            ResolutionGraph graph) {
        Coordinate coordinate = new Coordinate(
                node.packageId().groupId(),
                node.packageId().artifactId(),
                Optional.of(node.selectedVersion()));
        CachedArtifact pom = context.getPom(coordinate);
        CachedArtifact jar = selectedScope.artifactDescriptor()
                .map(context::getArtifact)
                .orElseGet(() -> context.getJar(coordinate));
        return new LockPackage(
                node.packageId(),
                node.selectedVersion(),
                "maven-central",
                selectedScope.scope(),
                selectedScope.direct(),
                Optional.of(jar.repositoryPath()),
                Optional.of(pom.repositoryPath()),
                Optional.of(sha256(jar.bytes())),
                Optional.of(sha256(pom.bytes())),
                dependenciesFor(node, graph));
    }

    private static List<String> dependenciesFor(PackageNode node, ResolutionGraph graph) {
        return graph.edges().stream()
                .filter(edge -> edge.from().equals(node))
                .map(edge -> edge.to().packageId() + ":" + edge.to().selectedVersion())
                .sorted()
                .toList();
    }

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                hex.append(String.format("%02x", value));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new ResolveException("Could not compute artifact checksum because SHA-256 is unavailable.", exception);
        }
    }

    private record SelectedScope(
            DependencyScope scope,
            boolean direct,
            Optional<ArtifactDescriptor> artifactDescriptor) {
        SelectedScope(DependencyScope scope, boolean direct) {
            this(scope, direct, Optional.empty());
        }

        SelectedScope {
            artifactDescriptor = artifactDescriptor == null ? Optional.empty() : artifactDescriptor;
        }

        SelectedScope merge(SelectedScope other) {
            return new SelectedScope(
                    scope,
                    direct || other.direct,
                    artifactDescriptor.isPresent() ? artifactDescriptor : other.artifactDescriptor);
        }
    }

    private record ResolutionState(ResolutionGraph graph, VersionSelectionResult selection) {
    }

    @FunctionalInterface
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(DependencyMetadataSource source);
    }

    private final class RepositoryContext implements DependencyMetadataSource {
        private final ProjectConfig config;
        private final LocalArtifactCache cache;
        private final boolean offline;
        private final Map<String, EffectiveRawPom> metadata = new HashMap<>();
        private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();
        private int downloadCount;

        RepositoryContext(ProjectConfig config, LocalArtifactCache cache, boolean offline) {
            this.config = config;
            this.cache = cache;
            this.offline = offline;
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            return effectivePom(coordinate, List.of());
        }

        CachedArtifact getPom(Coordinate coordinate) {
            if (offline) {
                return cache.getCachedPom(coordinate);
            }
            Path before = cache.pomPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchPom(coordinate, requested ->
                    repositoryClient.fetchPom(repositoryUri(), requested));
            if (!cached) {
                downloadCount++;
            }
            return artifact;
        }

        CachedArtifact getJar(Coordinate coordinate) {
            if (offline) {
                return cache.getCachedJar(coordinate);
            }
            Path before = cache.jarPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchJar(coordinate, requested ->
                    repositoryClient.fetchJar(repositoryUri(), requested));
            if (!cached) {
                downloadCount++;
            }
            return artifact;
        }

        CachedArtifact getArtifact(ArtifactDescriptor descriptor) {
            if (offline) {
                return cache.getCachedArtifact(descriptor, descriptor.extension().toUpperCase(java.util.Locale.ROOT));
            }
            Path before = cache.artifactPath(descriptor);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchArtifact(descriptor, requested ->
                    repositoryClient.fetchArtifact(repositoryUri(), descriptor));
            if (!cached) {
                downloadCount++;
            }
            return artifact;
        }

        int downloadCount() {
            return downloadCount;
        }

        Map<PackageId, String> projectManagedVersions() {
            Map<PackageId, String> versions = new LinkedHashMap<>();
            config.platforms().entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .forEach(platform -> {
                        Coordinate coordinate = coordinateParser.parse(platform.getKey() + ":" + platform.getValue());
                        EffectiveRawPom pom = effectivePom(coordinate, List.of());
                        for (RawPomDependency dependency : pom.dependencyManagement()) {
                            if (dependency.classifier().isPresent()) {
                                continue;
                            }
                            RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                            if (managedJarDependency(interpolated) && interpolated.version().isPresent()) {
                                versions.put(
                                        new PackageId(interpolated.groupId(), interpolated.artifactId()),
                                        interpolated.version().orElseThrow());
                            }
                        }
                    });
            return versions;
        }

        private EffectiveRawPom effectivePom(Coordinate coordinate, List<String> importStack) {
            String key = coordinate.toString();
            EffectiveRawPom cached = metadata.get(key);
            if (cached != null) {
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

            CachedArtifact pomArtifact = getPom(coordinate);
            RawPom rawPom = rawPomParser.parse(pomArtifact.bytes());
            List<RawPom> parents = loadParents(rawPom);
            String groupId = rawPom.groupId().or(() -> nearestGroupId(parents)).orElse(coordinate.groupId());
            String version = rawPom.version().or(() -> nearestVersion(parents)).orElse(coordinate.version().orElseThrow());
            Map<String, String> properties = inheritedProperties(rawPom, parents);
            List<RawPomDependency> dependencyManagement = inheritedDependencyManagement(rawPom, parents);
            EffectiveRawPom base = new EffectiveRawPom(rawPom, parents, groupId, version, properties, dependencyManagement);
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
            return effective;
        }

        private List<RawPom> loadParents(RawPom rawPom) {
            List<RawPom> nearestFirst = new ArrayList<>();
            RawPom current = rawPom;
            while (current.parent().isPresent()) {
                var parent = current.parent().orElseThrow();
                Coordinate parentCoordinate = new Coordinate(parent.groupId(), parent.artifactId(), Optional.of(parent.version()));
                RawPom parentPom = rawPomParser.parse(getPom(parentCoordinate).bytes());
                nearestFirst.add(parentPom);
                current = parentPom;
            }
            List<RawPom> rootFirst = new ArrayList<>();
            for (int index = nearestFirst.size() - 1; index >= 0; index--) {
                rootFirst.add(nearestFirst.get(index));
            }
            return rootFirst;
        }

        private URI repositoryUri() {
            return config.repositories().values().stream()
                    .sorted()
                    .map(URI::create)
                    .findFirst()
                    .orElseThrow(() -> new ResolveException("No repositories are configured in zolt.toml."));
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
                RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
                if (isImportedBom(interpolated)) {
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
                    dependencies.addAll(imported.dependencyManagement().stream()
                            .map(importedDependency -> importedDependency.classifier().isPresent()
                                    ? importedDependency
                                    : interpolator.interpolateDependency(importedDependency, imported))
                            .toList());
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

        private static boolean managedJarDependency(RawPomDependency dependency) {
            return dependency.type().orElse("jar").equals("jar")
                    && dependency.classifier().isEmpty();
        }
    }
}
