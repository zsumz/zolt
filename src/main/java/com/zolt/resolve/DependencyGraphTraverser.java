package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.PomDependencyManager;
import com.zolt.maven.RawPomDependency;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.TreeSet;

public final class DependencyGraphTraverser {
    private final DependencyMetadataSource metadataSource;
    private final PomDependencyManager dependencyManager;
    private final DependencyNormalizer normalizer;
    private final DependencyTraversalPolicy traversalPolicy;

    public DependencyGraphTraverser(DependencyMetadataSource metadataSource) {
        this(
                metadataSource,
                new PomDependencyManager(),
                new DependencyNormalizer(),
                new DependencyTraversalPolicy());
    }

    DependencyGraphTraverser(
            DependencyMetadataSource metadataSource,
            PomDependencyManager dependencyManager,
            DependencyNormalizer normalizer,
            DependencyTraversalPolicy traversalPolicy) {
        this.metadataSource = metadataSource;
        this.dependencyManager = dependencyManager;
        this.normalizer = normalizer;
        this.traversalPolicy = traversalPolicy;
    }

    public ResolutionGraph traverse(List<DependencyRequest> directRequests) {
        SequencedMap<NodeKey, PackageNode> nodes = new LinkedHashMap<>();
        List<ResolutionEdge> edges = new ArrayList<>();
        Set<VisitKey> visited = new TreeSet<>();
        ArrayDeque<TraversalItem> queue = new ArrayDeque<>();

        directRequests.stream()
                .sorted(Comparator.comparing(DependencyGraphTraverser::requestSortKey))
                .forEach(request -> queue.add(TraversalItem.direct(request)));

        while (!queue.isEmpty()) {
            TraversalItem item = queue.removeFirst();
            String version = requireVersion(item.request());
            PackageNode node = node(item.request(), version);
            NodeKey nodeKey = NodeKey.from(node);
            nodes.putIfAbsent(nodeKey, node);

            item.parent().ifPresent(parent -> edges.add(new ResolutionEdge(
                    parent,
                    node,
                    item.request(),
                    item.decision())));

            if (!visited.add(VisitKey.from(node, item.request().scope()))) {
                continue;
            }

            EffectiveRawPom pom = metadataSource.load(new Coordinate(
                    node.packageId().groupId(),
                    node.packageId().artifactId(),
                    Optional.of(node.selectedVersion())));
            List<NormalizedDependency> dependencies = dependencyManager.applyManagedVersions(pom).stream()
                    .map(normalizer::normalize)
                    .sorted(Comparator.comparing(DependencyGraphTraverser::dependencySortKey))
                    .toList();

            for (NormalizedDependency dependency : dependencies) {
                Coordinate candidate = coordinate(dependency.rawDependency());
                if (item.excludes(candidate)) {
                    continue;
                }

                DependencyTraversalDecision decision = traversalPolicy.decide(dependency, false);
                if (!decision.included()) {
                    continue;
                }
                Optional<DependencyScope> requestScope = transitiveScope(item.request().scope(), dependency.scope());
                if (requestScope.isEmpty()) {
                    continue;
                }

                String requestedVersion = dependency.rawDependency().version()
                        .orElseThrow(() -> new GraphTraversalException(
                                "Dependency "
                                        + dependency.rawDependency().groupId()
                                        + ":"
                                        + dependency.rawDependency().artifactId()
                                        + " from "
                                        + node.packageId()
                                        + ":"
                                        + node.selectedVersion()
                                        + " does not declare or inherit a version."));
                DependencyRequest request = new DependencyRequest(
                        new PackageId(dependency.rawDependency().groupId(), dependency.rawDependency().artifactId()),
                        requestedVersion,
                        requestScope.orElseThrow(),
                        RequestOrigin.TRANSITIVE);
                queue.add(TraversalItem.transitive(node, request, dependency.exclusions(), decision));
            }
        }

        return new ResolutionGraph(List.copyOf(nodes.values()), List.copyOf(edges), List.of());
    }

    private static PackageNode node(DependencyRequest request, String version) {
        return new PackageNode(request.packageId(), version);
    }

    private static String requireVersion(DependencyRequest request) {
        if (request.requestedVersion() == null || request.requestedVersion().isBlank()) {
            throw new GraphTraversalException(
                    "Dependency request for " + request.packageId() + " must include a version.");
        }
        return request.requestedVersion();
    }

    private static Coordinate coordinate(RawPomDependency dependency) {
        return new Coordinate(dependency.groupId(), dependency.artifactId(), dependency.version());
    }

    private static Optional<DependencyScope> transitiveScope(DependencyScope parentScope, DependencyScope dependencyScope) {
        if (dependencyScope == DependencyScope.TEST || dependencyScope == DependencyScope.PROVIDED) {
            return Optional.empty();
        }
        if (parentScope == DependencyScope.PROCESSOR) {
            return Optional.of(DependencyScope.PROCESSOR);
        }
        if (parentScope == DependencyScope.TEST_PROCESSOR) {
            return Optional.of(DependencyScope.TEST_PROCESSOR);
        }
        if (parentScope == DependencyScope.TEST) {
            return Optional.of(DependencyScope.TEST);
        }
        if (parentScope == DependencyScope.RUNTIME) {
            return Optional.of(DependencyScope.RUNTIME);
        }
        if (parentScope == DependencyScope.DEV) {
            return Optional.of(DependencyScope.DEV);
        }
        if (parentScope == DependencyScope.COMPILE) {
            return Optional.of(dependencyScope);
        }
        return Optional.empty();
    }

    private static String requestSortKey(DependencyRequest request) {
        return request.packageId() + ":" + request.requestedVersion() + ":" + request.scope();
    }

    private static String dependencySortKey(NormalizedDependency dependency) {
        RawPomDependency raw = dependency.rawDependency();
        return raw.groupId()
                + ":"
                + raw.artifactId()
                + ":"
                + raw.version().orElse("")
                + ":"
                + dependency.scope();
    }

    private record NodeKey(PackageId packageId, String version) implements Comparable<NodeKey> {
        static NodeKey from(PackageNode node) {
            return new NodeKey(node.packageId(), node.selectedVersion());
        }

        @Override
        public int compareTo(NodeKey other) {
            int packageCompared = packageId.toString().compareTo(other.packageId.toString());
            if (packageCompared != 0) {
                return packageCompared;
            }
            return version.compareTo(other.version);
        }
    }

    private record VisitKey(PackageId packageId, String version, DependencyScope scope) implements Comparable<VisitKey> {
        static VisitKey from(PackageNode node, DependencyScope scope) {
            return new VisitKey(node.packageId(), node.selectedVersion(), scope);
        }

        @Override
        public int compareTo(VisitKey other) {
            int packageCompared = packageId.toString().compareTo(other.packageId.toString());
            if (packageCompared != 0) {
                return packageCompared;
            }
            int versionCompared = version.compareTo(other.version);
            if (versionCompared != 0) {
                return versionCompared;
            }
            return scope.compareTo(other.scope);
        }
    }

    private record TraversalItem(
            Optional<PackageNode> parent,
            DependencyRequest request,
            List<DependencyExclusion> edgeExclusions,
            DependencyTraversalDecision decision) {
        TraversalItem {
            parent = parent == null ? Optional.empty() : parent;
            edgeExclusions = List.copyOf(edgeExclusions);
        }

        static TraversalItem direct(DependencyRequest request) {
            return new TraversalItem(
                    Optional.empty(),
                    request,
                    List.of(),
                    DependencyTraversalDecision.include("direct dependency"));
        }

        static TraversalItem transitive(
                PackageNode parent,
                DependencyRequest request,
                List<DependencyExclusion> edgeExclusions,
                DependencyTraversalDecision decision) {
            return new TraversalItem(Optional.of(parent), request, edgeExclusions, decision);
        }

        boolean excludes(Coordinate coordinate) {
            return edgeExclusions.stream().anyMatch(exclusion -> exclusion.matches(coordinate));
        }
    }
}
