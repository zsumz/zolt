package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.PomDependencyManager;
import com.zolt.maven.RawPomDependency;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyPolicySettings;
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
    private final DependencyRelocator relocator;
    private final List<GlobalExclusion> globalExclusions;
    private final Map<PackageId, DependencyConstraint> strictConstraints;

    public DependencyGraphTraverser(DependencyMetadataSource metadataSource) {
        this(metadataSource, DependencyPolicySettings.defaults());
    }

    public DependencyGraphTraverser(
            DependencyMetadataSource metadataSource,
            DependencyPolicySettings dependencyPolicy) {
        this(
                metadataSource,
                new PomDependencyManager(),
                new DependencyNormalizer(),
                new DependencyTraversalPolicy(),
                dependencyPolicy);
    }

    DependencyGraphTraverser(
            DependencyMetadataSource metadataSource,
            PomDependencyManager dependencyManager,
            DependencyNormalizer normalizer,
            DependencyTraversalPolicy traversalPolicy,
            DependencyPolicySettings dependencyPolicy) {
        this.metadataSource = metadataSource;
        this.dependencyManager = dependencyManager;
        this.normalizer = normalizer;
        this.traversalPolicy = traversalPolicy;
        this.relocator = new DependencyRelocator(metadataSource);
        this.globalExclusions = globalExclusions(dependencyPolicy);
        this.strictConstraints = strictConstraints(dependencyPolicy);
    }

    public ResolutionGraph traverse(List<DependencyRequest> directRequests) {
        SequencedMap<NodeKey, PackageNode> nodes = new LinkedHashMap<>();
        List<ResolutionEdge> edges = new ArrayList<>();
        List<DependencyPolicyEffect> policyEffects = new ArrayList<>();
        Set<VisitKey> visited = new TreeSet<>();
        ArrayDeque<TraversalItem> queue = new ArrayDeque<>();

        directRequests.stream()
                .sorted(Comparator.comparing(DependencyGraphTraverser::requestSortKey))
                .forEach(request -> queue.add(TraversalItem.direct(request)));

        while (!queue.isEmpty()) {
            List<TraversalItem> frontier = frontier(queue);
            metadataSource.preload(frontier.stream()
                    .map(item -> coordinate(item.request()))
                    .sorted(Comparator.comparing(Coordinate::toString))
                    .toList());
            for (TraversalItem item : frontier) {
                DependencyRelocator.RelocationResult relocated = relocator.relocateWithPom(item.request());
                DependencyRequest request = relocated.request();
                String version = requireVersion(request);
                PackageNode node = node(request, version);
                NodeKey nodeKey = NodeKey.from(node);
                nodes.putIfAbsent(nodeKey, node);

                item.parent().ifPresent(parent -> edges.add(new ResolutionEdge(
                        parent,
                        node,
                        request,
                        item.decision())));

                if (!visited.add(VisitKey.from(node, item.request().scope()))) {
                    continue;
                }

                EffectiveRawPom pom = relocated.pom();
                List<NormalizedDependency> dependencies = dependencyManager.applyManagedVersions(pom).stream()
                        .map(normalizer::normalize)
                        .sorted(Comparator.comparing(DependencyGraphTraverser::dependencySortKey))
                        .toList();

                for (NormalizedDependency dependency : dependencies) {
                    Coordinate candidate = coordinate(dependency.rawDependency());
                    List<DependencyPolicyEffect> exclusionEffects = exclusionEffects(item, node, dependency, candidate);
                    if (!exclusionEffects.isEmpty()) {
                        policyEffects.addAll(exclusionEffects);
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

                    PackageId packageId = new PackageId(
                            dependency.rawDependency().groupId(),
                            dependency.rawDependency().artifactId());
                    DependencyConstraint constraint = strictConstraints.get(packageId);
                    Optional<String> originalRequestedVersion = dependency.rawDependency().version();
                    String requestedVersion = constraint == null
                            ? originalRequestedVersion
                                    .orElseThrow(() -> new GraphTraversalException(
                                            "Dependency "
                                                    + dependency.rawDependency().groupId()
                                                    + ":"
                                                    + dependency.rawDependency().artifactId()
                                                    + " from "
                                                    + node.packageId()
                                                    + ":"
                                                    + node.selectedVersion()
                                                    + " does not declare or inherit a version."))
                            : constraint.version();
                    if (constraint != null) {
                        policyEffects.add(strictVersionEffect(
                                packageId,
                                originalRequestedVersion,
                                node,
                                constraint));
                    }
                    DependencyRequest transitiveRequest = new DependencyRequest(
                            packageId,
                            requestedVersion,
                            requestScope.orElseThrow(),
                            RequestOrigin.TRANSITIVE);
                    queue.add(TraversalItem.transitive(node, transitiveRequest, dependency.exclusions(), decision));
                }
            }
        }

        return new ResolutionGraph(
                List.copyOf(nodes.values()),
                List.copyOf(edges),
                List.of(),
                sortedPolicyEffects(policyEffects));
    }

    private static List<TraversalItem> frontier(ArrayDeque<TraversalItem> queue) {
        int size = queue.size();
        List<TraversalItem> frontier = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            frontier.add(queue.removeFirst());
        }
        return frontier;
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

    private static Coordinate coordinate(DependencyRequest request) {
        return new Coordinate(
                request.packageId().groupId(),
                request.packageId().artifactId(),
                Optional.ofNullable(request.requestedVersion()));
    }

    private static List<GlobalExclusion> globalExclusions(DependencyPolicySettings dependencyPolicy) {
        if (dependencyPolicy == null) {
            return List.of();
        }
        return dependencyPolicy.exclusions().stream()
                .map(exclusion -> new GlobalExclusion(
                        new DependencyExclusion(exclusion.group(), exclusion.artifact()),
                        exclusion.reason()))
                .toList();
    }

    private static Map<PackageId, DependencyConstraint> strictConstraints(DependencyPolicySettings dependencyPolicy) {
        if (dependencyPolicy == null) {
            return Map.of();
        }
        Map<PackageId, DependencyConstraint> constraints = new LinkedHashMap<>();
        for (DependencyConstraint constraint : dependencyPolicy.constraints().values()) {
            String[] parts = constraint.coordinate().split(":", -1);
            if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
                throw new GraphTraversalException(
                        "Invalid dependency constraint coordinate `"
                                + constraint.coordinate()
                                + "`. Use `group:artifact`.");
            }
            constraints.put(new PackageId(parts[0], parts[1]), constraint);
        }
        return Map.copyOf(constraints);
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
        if (parentScope == DependencyScope.QUARKUS_DEPLOYMENT) {
            return Optional.of(DependencyScope.QUARKUS_DEPLOYMENT);
        }
        if (parentScope == DependencyScope.TOOL_OPENAPI) {
            return Optional.of(DependencyScope.TOOL_OPENAPI);
        }
        if (parentScope == DependencyScope.TOOL_COVERAGE) {
            return Optional.of(DependencyScope.TOOL_COVERAGE);
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

    private List<DependencyPolicyEffect> exclusionEffects(
            TraversalItem item,
            PackageNode source,
            NormalizedDependency dependency,
            Coordinate coordinate) {
        List<DependencyPolicyEffect> effects = new ArrayList<>();
        item.matchingExclusions(coordinate).stream()
                .map(exclusion -> new DependencyPolicyEffect(
                        "edge-exclusion",
                        new PackageId(coordinate.groupId(), coordinate.artifactId()),
                        dependency.rawDependency().version(),
                        Optional.of(coordinate(source)),
                        "dependency edge exclusion from "
                                + coordinate(source)
                                + " to "
                                + coordinate.groupId()
                                + ":"
                                + coordinate.artifactId()))
                .forEach(effects::add);
        matchingGlobalExclusions(coordinate).stream()
                .map(exclusion -> new DependencyPolicyEffect(
                        "global-exclusion",
                        new PackageId(coordinate.groupId(), coordinate.artifactId()),
                        dependency.rawDependency().version(),
                        Optional.of(coordinate(source)),
                        exclusion.reason()
                                .map(reason -> "[dependencyPolicy].exclude "
                                        + coordinate.groupId()
                                        + ":"
                                        + coordinate.artifactId()
                                        + " ("
                                        + reason
                                        + ")")
                                .orElse("[dependencyPolicy].exclude "
                                        + coordinate.groupId()
                                        + ":"
                                        + coordinate.artifactId())))
                .forEach(effects::add);
        return effects;
    }

    private List<GlobalExclusion> matchingGlobalExclusions(Coordinate coordinate) {
        return globalExclusions.stream()
                .filter(exclusion -> exclusion.exclusion().matches(coordinate))
                .toList();
    }

    private static DependencyPolicyEffect strictVersionEffect(
            PackageId packageId,
            Optional<String> requestedVersion,
            PackageNode source,
            DependencyConstraint constraint) {
        String policy = "strict-version: "
                + packageId
                + " requested "
                + requestedVersion.orElse("<missing>")
                + " -> "
                + constraint.version();
        return new DependencyPolicyEffect(
                "strict-version",
                packageId,
                requestedVersion,
                Optional.of(coordinate(source)),
                constraint.reason()
                        .map(reason -> policy + " (" + reason + ")")
                        .orElse(policy));
    }

    private static List<DependencyPolicyEffect> sortedPolicyEffects(List<DependencyPolicyEffect> policyEffects) {
        return policyEffects.stream()
                .distinct()
                .sorted(Comparator.comparing(policyEffect -> policyEffect.kind()
                        + ":"
                        + policyEffect.packageId()
                        + ":"
                        + policyEffect.requestedVersion().orElse("")
                        + ":"
                        + policyEffect.source().orElse("")
                        + ":"
                        + policyEffect.policy()))
                .toList();
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

    private static String coordinate(PackageNode node) {
        return node.packageId() + ":" + node.selectedVersion();
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
                    request.exclusions(),
                    DependencyTraversalDecision.include("direct dependency"));
        }

        static TraversalItem transitive(
                PackageNode parent,
                DependencyRequest request,
                List<DependencyExclusion> edgeExclusions,
                DependencyTraversalDecision decision) {
            return new TraversalItem(Optional.of(parent), request, edgeExclusions, decision);
        }

        List<DependencyExclusion> matchingExclusions(Coordinate coordinate) {
            return edgeExclusions.stream()
                    .filter(exclusion -> exclusion.matches(coordinate))
                    .toList();
        }
    }

    private record GlobalExclusion(
            DependencyExclusion exclusion,
            Optional<String> reason) {
        private GlobalExclusion {
            reason = reason == null ? Optional.empty() : reason;
        }
    }
}
