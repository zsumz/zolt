package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.EffectiveRawPom;
import com.zolt.maven.RawPom;
import com.zolt.maven.RawPomDependency;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class EffectivePomInheritanceBuilder {
    EffectiveRawPom build(Coordinate coordinate, RawPom rawPom, List<RawPom> parents) {
        String groupId = rawPom.groupId().or(() -> nearestGroupId(parents)).orElse(coordinate.groupId());
        String version = rawPom.version()
                .or(() -> nearestVersion(parents))
                .orElse(coordinate.version().orElseThrow());
        return new EffectiveRawPom(
                rawPom,
                parents,
                groupId,
                version,
                inheritedProperties(rawPom, parents),
                inheritedDependencyManagement(rawPom, parents));
    }

    private static Optional<String> nearestGroupId(List<RawPom> parents) {
        for (int index = parents.size() - 1; index >= 0; index--) {
            if (parents.get(index).groupId().isPresent()) {
                return parents.get(index).groupId();
            }
        }
        return Optional.empty();
    }

    private static Optional<String> nearestVersion(List<RawPom> parents) {
        for (int index = parents.size() - 1; index >= 0; index--) {
            if (parents.get(index).version().isPresent()) {
                return parents.get(index).version();
            }
        }
        return Optional.empty();
    }

    private static Map<String, String> inheritedProperties(RawPom rawPom, List<RawPom> parents) {
        Map<String, String> properties = new LinkedHashMap<>();
        for (RawPom parent : parents) {
            properties.putAll(parent.properties());
        }
        properties.putAll(rawPom.properties());
        return properties;
    }

    private static List<RawPomDependency> inheritedDependencyManagement(RawPom rawPom, List<RawPom> parents) {
        List<RawPomDependency> dependencies = new ArrayList<>();
        for (RawPom parent : parents) {
            dependencies.addAll(parent.dependencyManagement());
        }
        dependencies.addAll(rawPom.dependencyManagement());
        return dependencies;
    }
}
