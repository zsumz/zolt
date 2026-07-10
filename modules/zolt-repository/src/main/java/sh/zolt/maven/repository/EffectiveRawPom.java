package sh.zolt.maven.repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record EffectiveRawPom(
        RawPom rawPom,
        List<RawPom> parents,
        String groupId,
        String version,
        Map<String, String> properties,
        List<RawPomDependency> dependencyManagement) {
    public EffectiveRawPom {
        parents = List.copyOf(parents);
        properties = Map.copyOf(properties);
        dependencyManagement = List.copyOf(dependencyManagement);
    }

    public List<RawPomDependency> dependencies() {
        Map<DependencyKey, RawPomDependency> dependencies = new LinkedHashMap<>();
        for (RawPom parent : parents) {
            putDependencies(dependencies, parent.dependencies());
        }
        putDependencies(dependencies, rawPom.dependencies());
        return List.copyOf(dependencies.values());
    }

    private static void putDependencies(
            Map<DependencyKey, RawPomDependency> dependencies,
            List<RawPomDependency> incoming) {
        for (RawPomDependency dependency : incoming) {
            dependencies.put(DependencyKey.from(dependency), dependency);
        }
    }

    private record DependencyKey(
            String groupId,
            String artifactId,
            String type,
            Optional<String> classifier) {
        static DependencyKey from(RawPomDependency dependency) {
            return new DependencyKey(
                    dependency.groupId(),
                    dependency.artifactId(),
                    dependency.type().orElse("jar"),
                    dependency.classifier());
        }
    }
}
