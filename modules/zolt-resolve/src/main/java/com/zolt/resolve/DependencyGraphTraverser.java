package com.zolt.resolve;

import com.zolt.dependency.PackageId;
import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.PomDependencyManager;
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
    private final DependencyTraversalCandidateSelector candidateSelector;
    private final DependencyRelocator relocator;

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
        this.relocator = new DependencyRelocator(metadataSource);
        this.candidateSelector = new DependencyTraversalCandidateSelector(
                traversalPolicy,
                new DependencyTransitiveScopeSelector(),
                globalExclusions(dependencyPolicy),
                strictConstraints(dependencyPolicy));
    }

    public ResolutionGraph traverse(List<DependencyRequest> directRequests) {
        SequencedMap<DependencyTraversalNodeKey, PackageNode> nodes = new LinkedHashMap<>();
        List<ResolutionEdge> edges = new ArrayList<>();
        List<DependencyPolicyEffect> policyEffects = new ArrayList<>();
        Set<DependencyTraversalVisitKey> visited = new TreeSet<>();
        ArrayDeque<DependencyTraversalItem> queue = new ArrayDeque<>();

        directRequests.stream()
                .sorted(Comparator.comparing(DependencyTraversalOrdering::requestSortKey))
                .forEach(request -> queue.add(DependencyTraversalItem.direct(request)));

        while (!queue.isEmpty()) {
            List<DependencyTraversalItem> frontier = frontier(queue);
            metadataSource.preload(frontier.stream()
                    .map(item -> coordinate(item.request()))
                    .sorted(Comparator.comparing(Coordinate::toString))
                    .toList());
            for (DependencyTraversalItem item : frontier) {
                DependencyRelocator.RelocationResult relocated = relocator.relocateWithPom(item.request());
                DependencyRequest request = relocated.request();
                String version = requireVersion(request);
                PackageNode node = node(request, version);
                DependencyTraversalNodeKey nodeKey = DependencyTraversalNodeKey.from(node);
                nodes.putIfAbsent(nodeKey, node);

                item.parent().ifPresent(parent -> edges.add(new ResolutionEdge(
                        parent,
                        node,
                        request,
                        item.decision())));

                if (!visited.add(DependencyTraversalVisitKey.from(node, item.request().scope()))) {
                    continue;
                }

                EffectiveRawPom pom = relocated.pom();
                List<NormalizedDependency> dependencies = dependencyManager.applyManagedVersions(pom).stream()
                        .map(normalizer::normalize)
                        .sorted(Comparator.comparing(DependencyTraversalOrdering::dependencySortKey))
                        .toList();

                for (NormalizedDependency dependency : dependencies) {
                    DependencyTraversalSelection selection = candidateSelector.select(
                            new DependencyTraversalCandidate(item, node, dependency));
                    policyEffects.addAll(selection.policyEffects());
                    selection.selectedItem().ifPresent(queue::add);
                }
            }
        }

        return new ResolutionGraph(
                List.copyOf(nodes.values()),
                List.copyOf(edges),
                List.of(),
                DependencyTraversalOrdering.sortedPolicyEffects(policyEffects));
    }

    private static List<DependencyTraversalItem> frontier(ArrayDeque<DependencyTraversalItem> queue) {
        int size = queue.size();
        List<DependencyTraversalItem> frontier = new ArrayList<>(size);
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

    private static Coordinate coordinate(DependencyRequest request) {
        return new Coordinate(
                request.packageId().groupId(),
                request.packageId().artifactId(),
                Optional.ofNullable(request.requestedVersion()));
    }

    private static List<DependencyGlobalExclusion> globalExclusions(DependencyPolicySettings dependencyPolicy) {
        if (dependencyPolicy == null) {
            return List.of();
        }
        return dependencyPolicy.exclusions().stream()
                .map(exclusion -> new DependencyGlobalExclusion(
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

}
