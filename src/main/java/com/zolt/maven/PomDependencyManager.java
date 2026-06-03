package com.zolt.maven;

import java.util.LinkedHashMap;
import java.util.Map;
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

        RawPomDependency managed = managedDependencies(pom).get(key(interpolated));
        if (managed == null || managed.version().isEmpty()) {
            return interpolated;
        }

        return new RawPomDependency(
                interpolated.groupId(),
                interpolated.artifactId(),
                managed.version(),
                interpolated.scope(),
                interpolated.type(),
                interpolated.classifier(),
                interpolated.optional(),
                interpolated.exclusions());
    }

    public java.util.List<RawPomDependency> applyManagedVersions(EffectiveRawPom pom) {
        return pom.rawPom().dependencies().stream()
                .filter(dependency -> dependency.classifier().isEmpty())
                .map(dependency -> applyManagedVersion(dependency, pom))
                .toList();
    }

    private Map<ManagedDependencyKey, RawPomDependency> managedDependencies(EffectiveRawPom pom) {
        Map<ManagedDependencyKey, RawPomDependency> dependencies = new LinkedHashMap<>();
        for (RawPomDependency dependency : pom.dependencyManagement()) {
            if (dependency.classifier().isPresent()) {
                continue;
            }
            RawPomDependency interpolated = interpolator.interpolateDependency(dependency, pom);
            dependencies.put(key(interpolated), interpolated);
        }
        return dependencies;
    }

    private ManagedDependencyKey key(RawPomDependency dependency) {
        return new ManagedDependencyKey(
                dependency.groupId(),
                dependency.artifactId(),
                dependency.type().orElse("jar"),
                dependency.classifier());
    }

    private record ManagedDependencyKey(
            String groupId,
            String artifactId,
            String type,
            Optional<String> classifier) {
    }
}
