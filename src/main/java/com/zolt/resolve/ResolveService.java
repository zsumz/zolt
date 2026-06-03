package com.zolt.resolve;

import com.zolt.cache.CachedArtifact;
import com.zolt.cache.LocalArtifactCache;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.lockfile.ZoltLockfileWriter;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.MavenRepositoryClient;
import com.zolt.maven.PomPropertyInterpolator;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import com.zolt.maven.RawPomParser;
import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;

public final class ResolveService {
    private final CoordinateParser coordinateParser;
    private final MavenRepositoryClient repositoryClient;
    private final RawPomParser rawPomParser;
    private final DependencyGraphTraverserFactory graphTraverserFactory;
    private final VersionSelector versionSelector;
    private final ZoltLockfileWriter lockfileWriter;

    public ResolveService() {
        this(
                new CoordinateParser(),
                new MavenRepositoryClient(),
                new RawPomParser(),
                DependencyGraphTraverser::new,
                new VersionSelector(),
                new ZoltLockfileWriter());
    }

    ResolveService(
            CoordinateParser coordinateParser,
            MavenRepositoryClient repositoryClient,
            RawPomParser rawPomParser,
            DependencyGraphTraverserFactory graphTraverserFactory,
            VersionSelector versionSelector,
            ZoltLockfileWriter lockfileWriter) {
        this.coordinateParser = coordinateParser;
        this.repositoryClient = repositoryClient;
        this.rawPomParser = rawPomParser;
        this.graphTraverserFactory = graphTraverserFactory;
        this.versionSelector = versionSelector;
        this.lockfileWriter = lockfileWriter;
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot) {
        return resolve(projectDirectory, config, cacheRoot, false);
    }

    public ResolveResult resolve(Path projectDirectory, ProjectConfig config, Path cacheRoot, boolean locked) {
        Path lockfilePath = projectDirectory.resolve("zolt.lock");
        if (locked && !Files.isRegularFile(lockfilePath)) {
            throw new ResolveException(
                    "Locked resolve requires zolt.lock at "
                            + lockfilePath
                            + ". Run `zolt resolve` to create it, then retry `zolt resolve --locked`.");
        }

        RepositoryContext context = new RepositoryContext(config, new LocalArtifactCache(cacheRoot));
        List<DependencyRequest> directRequests = directRequests(config, context.projectManagedVersions());
        DependencyGraphTraverser traverser = graphTraverserFactory.create(context);
        ResolutionGraph graph = traverser.traverse(directRequests);
        VersionSelectionResult selection = versionSelector.select(directRequests, graph);
        ZoltLockfile lockfile = lockfile(context, graph, selection, directRequests);
        if (locked) {
            verifyLocked(lockfilePath, lockfile);
        } else {
            lockfileWriter.write(lockfilePath, lockfile);
        }
        return new ResolveResult(
                lockfile.packages().size(),
                context.downloadCount(),
                lockfile.conflicts().size(),
                lockfilePath);
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
        return requests;
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
        SequencedMap<PackageId, DependencyRequest> selectedRequests = selectedRequests(graph, selection, directRequests);
        List<LockPackage> packages = selection.selectedNodes().stream()
                .map(node -> lockPackage(context, node, selectedRequests.get(node.packageId()), graph))
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

    private SequencedMap<PackageId, DependencyRequest> selectedRequests(
            ResolutionGraph graph,
            VersionSelectionResult selection,
            List<DependencyRequest> directRequests) {
        SequencedMap<PackageId, DependencyRequest> requests = new LinkedHashMap<>();
        List<DependencyRequest> allRequests = new ArrayList<>();
        allRequests.addAll(directRequests);
        allRequests.addAll(graph.edges().stream().map(ResolutionEdge::request).toList());
        for (PackageNode node : selection.selectedNodes()) {
            allRequests.stream()
                    .filter(request -> request.packageId().equals(node.packageId()))
                    .filter(request -> request.requestedVersion().equals(node.selectedVersion()))
                    .findFirst()
                    .ifPresent(request -> requests.put(node.packageId(), request));
        }
        return requests;
    }

    private LockPackage lockPackage(
            RepositoryContext context,
            PackageNode node,
            DependencyRequest request,
            ResolutionGraph graph) {
        Coordinate coordinate = new Coordinate(
                node.packageId().groupId(),
                node.packageId().artifactId(),
                Optional.of(node.selectedVersion()));
        CachedArtifact pom = context.getPom(coordinate);
        CachedArtifact jar = context.getJar(coordinate);
        DependencyScope scope = request == null ? DependencyScope.COMPILE : request.scope();
        boolean direct = request != null && request.direct();
        return new LockPackage(
                node.packageId(),
                node.selectedVersion(),
                "maven-central",
                scope,
                direct,
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

    @FunctionalInterface
    interface DependencyGraphTraverserFactory {
        DependencyGraphTraverser create(DependencyMetadataSource source);
    }

    private final class RepositoryContext implements DependencyMetadataSource {
        private final ProjectConfig config;
        private final LocalArtifactCache cache;
        private final Map<String, EffectiveRawPom> metadata = new HashMap<>();
        private final PomPropertyInterpolator interpolator = new PomPropertyInterpolator();
        private int downloadCount;

        RepositoryContext(ProjectConfig config, LocalArtifactCache cache) {
            this.config = config;
            this.cache = cache;
        }

        @Override
        public EffectiveRawPom load(Coordinate coordinate) {
            return effectivePom(coordinate, List.of());
        }

        CachedArtifact getPom(Coordinate coordinate) {
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
            Path before = cache.jarPath(coordinate);
            boolean cached = java.nio.file.Files.isRegularFile(before);
            CachedArtifact artifact = cache.getOrFetchJar(coordinate, requested ->
                    repositoryClient.fetchJar(repositoryUri(), requested));
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
                            .map(importedDependency -> interpolator.interpolateDependency(importedDependency, imported))
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
