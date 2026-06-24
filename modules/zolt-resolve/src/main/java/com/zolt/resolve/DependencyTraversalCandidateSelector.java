package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.RawPomDependency;
import com.zolt.project.DependencyConstraint;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DependencyTraversalCandidateSelector {
    private final DependencyTraversalPolicy traversalPolicy;
    private final DependencyTransitiveScopeSelector transitiveScopeSelector;
    private final List<DependencyGlobalExclusion> globalExclusions;
    private final Map<PackageId, DependencyConstraint> strictConstraints;

    DependencyTraversalCandidateSelector(
            DependencyTraversalPolicy traversalPolicy,
            DependencyTransitiveScopeSelector transitiveScopeSelector,
            List<DependencyGlobalExclusion> globalExclusions,
            Map<PackageId, DependencyConstraint> strictConstraints) {
        this.traversalPolicy = traversalPolicy;
        this.transitiveScopeSelector = transitiveScopeSelector;
        this.globalExclusions = List.copyOf(globalExclusions);
        this.strictConstraints = Map.copyOf(strictConstraints);
    }

    DependencyTraversalSelection select(DependencyTraversalCandidate candidate) {
        NormalizedDependency dependency = candidate.dependency();
        Coordinate coordinate = coordinate(dependency.rawDependency());
        List<DependencyPolicyEffect> exclusionEffects = exclusionEffects(candidate, coordinate);
        if (!exclusionEffects.isEmpty()) {
            return DependencyTraversalSelection.skippedWithEffects(exclusionEffects);
        }

        DependencyTraversalDecision decision = traversalPolicy.decide(dependency, false);
        if (!decision.included()) {
            return DependencyTraversalSelection.skipped();
        }
        Optional<DependencyScope> requestScope = transitiveScopeSelector.select(
                candidate.item().request().scope(),
                dependency.scope());
        if (requestScope.isEmpty()) {
            return DependencyTraversalSelection.skipped();
        }

        PackageId packageId = new PackageId(
                dependency.rawDependency().groupId(),
                dependency.rawDependency().artifactId());
        DependencyConstraint constraint = strictConstraints.get(packageId);
        Optional<String> originalRequestedVersion = dependency.rawDependency().version();
        String requestedVersion = requestedVersion(candidate.source(), dependency, packageId, constraint);
        List<DependencyPolicyEffect> policyEffects = new ArrayList<>();
        if (constraint != null) {
            policyEffects.add(strictVersionEffect(
                    packageId,
                    originalRequestedVersion,
                    candidate.source(),
                    constraint));
        }
        DependencyRequest request = new DependencyRequest(
                packageId,
                requestedVersion,
                requestScope.orElseThrow(),
                RequestOrigin.TRANSITIVE,
                artifactDescriptor(packageId, requestedVersion, dependency.rawDependency()));
        return DependencyTraversalSelection.selected(
                DependencyTraversalItem.transitive(
                        candidate.source(),
                        request,
                        dependency.exclusions(),
                        decision),
                policyEffects);
    }

    private String requestedVersion(
            PackageNode source,
            NormalizedDependency dependency,
            PackageId packageId,
            DependencyConstraint constraint) {
        if (constraint != null) {
            return constraint.version();
        }
        return dependency.rawDependency().version()
                .orElseThrow(() -> new GraphTraversalException(
                        "Dependency "
                                + dependency.rawDependency().groupId()
                                + ":"
                                + dependency.rawDependency().artifactId()
                                + " from "
                                + source.packageId()
                                + ":"
                                + source.selectedVersion()
                                + " does not declare or inherit a version."));
    }

    private List<DependencyPolicyEffect> exclusionEffects(
            DependencyTraversalCandidate candidate,
            Coordinate coordinate) {
        List<DependencyPolicyEffect> effects = new ArrayList<>();
        candidate.item().matchingExclusions(coordinate).stream()
                .map(exclusion -> new DependencyPolicyEffect(
                        "edge-exclusion",
                        new PackageId(coordinate.groupId(), coordinate.artifactId()),
                        candidate.dependency().rawDependency().version(),
                        Optional.of(sourceCoordinate(candidate.source())),
                        "dependency edge exclusion from "
                                + sourceCoordinate(candidate.source())
                                + " to "
                                + coordinate.groupId()
                                + ":"
                                + coordinate.artifactId()))
                .forEach(effects::add);
        matchingGlobalExclusions(coordinate).stream()
                .map(exclusion -> new DependencyPolicyEffect(
                        "global-exclusion",
                        new PackageId(coordinate.groupId(), coordinate.artifactId()),
                        candidate.dependency().rawDependency().version(),
                        Optional.of(sourceCoordinate(candidate.source())),
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

    private List<DependencyGlobalExclusion> matchingGlobalExclusions(Coordinate coordinate) {
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
                Optional.of(sourceCoordinate(source)),
                constraint.reason()
                        .map(reason -> policy + " (" + reason + ")")
                        .orElse(policy));
    }

    private static Optional<ArtifactDescriptor> artifactDescriptor(
            PackageId packageId,
            String version,
            RawPomDependency dependency) {
        String extension = dependency.type().orElse("jar");
        if (dependency.classifier().isEmpty() && "jar".equals(extension)) {
            return Optional.empty();
        }
        Coordinate coordinate = new Coordinate(packageId.groupId(), packageId.artifactId(), Optional.of(version));
        return Optional.of(new ArtifactDescriptor(coordinate, dependency.classifier(), extension));
    }

    private static Coordinate coordinate(RawPomDependency dependency) {
        return new Coordinate(dependency.groupId(), dependency.artifactId(), dependency.version());
    }

    private static String sourceCoordinate(PackageNode node) {
        return node.packageId() + ":" + node.selectedVersion();
    }
}
