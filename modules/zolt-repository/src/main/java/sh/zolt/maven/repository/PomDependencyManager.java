package sh.zolt.maven.repository;

import java.util.Optional;

public final class PomDependencyManager {
    private final PomPropertyInterpolator interpolator;

    public PomDependencyManager() {
        this(new PomPropertyInterpolator());
    }

    PomDependencyManager(PomPropertyInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    public RawPomDependency applyManagedVersion(RawPomDependency dependency, EffectiveRawPom pom) {
        RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
        if (interpolated.version().isPresent()) {
            return interpolated;
        }

        Optional<RawPomDependency> managed = managedDependency(interpolated, pom);
        if (managed.isEmpty()) {
            return interpolated;
        }

        RawPomDependency managedDependency = managed.orElseThrow();
        if (managedDependency.version().isEmpty()) {
            return interpolated;
        }

        return new RawPomDependency(
                interpolated.groupId(),
                interpolated.artifactId(),
                managedDependency.version(),
                interpolated.scope().or(managedDependency::scope),
                interpolated.type().or(managedDependency::type),
                interpolated.classifier(),
                interpolated.optional(),
                unionExclusions(interpolated.exclusions(), managedDependency.exclusions()));
    }

    private static java.util.List<RawPomExclusion> unionExclusions(
            java.util.List<RawPomExclusion> requested,
            java.util.List<RawPomExclusion> managed) {
        if (managed.isEmpty()) {
            return requested;
        }
        java.util.LinkedHashSet<RawPomExclusion> union = new java.util.LinkedHashSet<>(requested);
        union.addAll(managed);
        return java.util.List.copyOf(union);
    }

    public java.util.List<RawPomDependency> applyManagedVersions(EffectiveRawPom pom) {
        return pom.dependencies().stream()
                .filter(PomDependencyManager::entersTransitiveGraphBeforeInterpolation)
                .map(dependency -> applyManagedVersion(dependency, pom))
                .filter(PomDependencyManager::entersTransitiveGraph)
                .toList();
    }

    private static boolean entersTransitiveGraphBeforeInterpolation(RawPomDependency dependency) {
        return !dependency.optional()
                && dependency.scope()
                        .map(PomDependencyManager::entersTransitiveGraph)
                        .orElse(true);
    }

    private static boolean entersTransitiveGraph(RawPomDependency dependency) {
        return dependency.scope()
                .map(PomDependencyManager::entersTransitiveGraph)
                .orElse(true);
    }

    private static boolean entersTransitiveGraph(String scope) {
        return !"test".equals(scope) && !"provided".equals(scope);
    }

    private Optional<RawPomDependency> managedDependency(RawPomDependency requested, EffectiveRawPom pom) {
        ManagedDependencyKey requestedKey = key(requested);
        RawPomDependency match = null;
        for (RawPomDependency dependency : pom.dependencyManagement()) {
            Optional<ManagedDependencyKey> dependencyKey = managedDependencyKey(dependency, pom);
            if (dependencyKey.isPresent() && dependencyKey.orElseThrow().equals(requestedKey)) {
                match = dependency;
            }
        }
        return Optional.ofNullable(match)
                .map(dependency -> interpolator.interpolateDependency(dependency, pom));
    }

    private ManagedDependencyKey key(RawPomDependency dependency) {
        return new ManagedDependencyKey(
                dependency.groupId(),
                dependency.artifactId(),
                dependency.type().orElse("jar"),
                dependency.classifier());
    }

    private Optional<ManagedDependencyKey> managedDependencyKey(RawPomDependency dependency, EffectiveRawPom pom) {
        try {
            return Optional.of(new ManagedDependencyKey(
                    interpolator.interpolate(dependency.groupId(), pom),
                    interpolator.interpolate(dependency.artifactId(), pom),
                    dependency.type()
                            .map(value -> interpolator.interpolate(value, pom))
                            .orElse("jar"),
                    dependency.classifier().map(value -> interpolator.interpolate(value, pom))));
        } catch (PomInterpolationException exception) {
            return Optional.empty();
        }
    }

    private record ManagedDependencyKey(
            String groupId,
            String artifactId,
            String type,
            Optional<String> classifier) {
    }
}
