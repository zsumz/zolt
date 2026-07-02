package sh.zolt.maven.repository;

import sh.zolt.maven.Coordinate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

final class ParentPomResolver {
    private final ParentPomSource source;

    public ParentPomResolver(ParentPomSource source) {
        this.source = source;
    }

    public EffectiveRawPom resolve(RawPom child) {
        List<RawPom> parents = loadParents(child);
        return new EffectiveRawPom(
                child,
                parents,
                inheritedGroupId(child, parents),
                inheritedVersion(child, parents),
                inheritedProperties(child, parents),
                inheritedDependencyManagement(child, parents));
    }

    private List<RawPom> loadParents(RawPom child) {
        List<RawPom> nearestFirst = new ArrayList<>();
        LinkedHashSet<String> visited = new LinkedHashSet<>();
        if (child.groupId().isPresent() && child.version().isPresent()) {
            visited.add(new Coordinate(
                            child.groupId().orElseThrow(),
                            child.artifactId(),
                            child.version())
                    .toString());
        }
        RawPom current = child;
        while (current.parent().isPresent()) {
            RawPomParent parent = current.parent().orElseThrow();
            Coordinate parentCoordinate = new Coordinate(
                    parent.groupId(),
                    parent.artifactId(),
                    java.util.Optional.of(parent.version()));
            String key = parentCoordinate.toString();
            if (!visited.add(key)) {
                throw new ParentPomException("Parent POM cycle detected: " + String.join(" -> ", visited) + " -> "
                        + key + ". Remove the circular <parent> reference from one of these POMs.");
            }
            RawPom parentPom = source.load(parentCoordinate);
            nearestFirst.add(parentPom);
            current = parentPom;
        }

        List<RawPom> rootFirst = new ArrayList<>();
        for (int index = nearestFirst.size() - 1; index >= 0; index--) {
            rootFirst.add(nearestFirst.get(index));
        }
        return rootFirst;
    }

    private static String inheritedGroupId(RawPom child, List<RawPom> parents) {
        return child.groupId()
                .or(() -> nearestParentGroupId(parents))
                .orElseThrow(() -> new ParentPomException(
                        "POM " + child.artifactId() + " does not declare or inherit a groupId."));
    }

    private static String inheritedVersion(RawPom child, List<RawPom> parents) {
        return child.version()
                .or(() -> nearestParentVersion(parents))
                .orElseThrow(() -> new ParentPomException(
                        "POM " + child.artifactId() + " does not declare or inherit a version."));
    }

    private static java.util.Optional<String> nearestParentGroupId(List<RawPom> parents) {
        for (int index = parents.size() - 1; index >= 0; index--) {
            RawPom parent = parents.get(index);
            if (parent.groupId().isPresent()) {
                return parent.groupId();
            }
        }
        return java.util.Optional.empty();
    }

    private static java.util.Optional<String> nearestParentVersion(List<RawPom> parents) {
        for (int index = parents.size() - 1; index >= 0; index--) {
            RawPom parent = parents.get(index);
            if (parent.version().isPresent()) {
                return parent.version();
            }
        }
        return java.util.Optional.empty();
    }

    private static Map<String, String> inheritedProperties(RawPom child, List<RawPom> parents) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (RawPom parent : parents) {
            properties.putAll(parent.properties());
        }
        properties.putAll(child.properties());
        return properties;
    }

    private static List<RawPomDependency> inheritedDependencyManagement(RawPom child, List<RawPom> parents) {
        List<RawPomDependency> dependencies = new ArrayList<>();
        for (RawPom parent : parents) {
            dependencies.addAll(parent.dependencyManagement());
        }
        dependencies.addAll(child.dependencyManagement());
        return dependencies;
    }
}
