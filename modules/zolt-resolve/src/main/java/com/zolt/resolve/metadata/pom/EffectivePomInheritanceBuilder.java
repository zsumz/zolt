package com.zolt.resolve.metadata.pom;

import com.zolt.maven.Coordinate;
import com.zolt.maven.repository.EffectiveRawPom;
import com.zolt.maven.repository.RawPom;
import com.zolt.maven.repository.RawPomDependency;
import com.zolt.resolve.traversal.GraphTraversalException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EffectivePomInheritanceBuilder {
    EffectiveRawPom build(Coordinate coordinate, RawPom rawPom, List<RawPom> parents) {
        EffectivePomInheritanceInput input = new EffectivePomInheritanceInput(coordinate, rawPom, parents);
        return normalize(input).toEffectiveRawPom(input.rawPom(), input.parents());
    }

    EffectivePomInheritanceResult normalize(EffectivePomInheritanceInput input) {
        String groupId = input.rawPom().groupId()
                .or(() -> nearestGroupId(input.parents()))
                .orElse(input.requestedCoordinate().groupId());
        String version = input.rawPom().version()
                .or(() -> nearestVersion(input.parents()))
                .orElseGet(() -> requestedVersion(input));
        return new EffectivePomInheritanceResult(
                groupId,
                version,
                inheritedProperties(input.rawPom(), input.parents()),
                inheritedDependencyManagement(input.rawPom(), input.parents()));
    }

    private static String requestedVersion(EffectivePomInheritanceInput input) {
        return input.requestedCoordinate().version().orElseThrow(() -> new GraphTraversalException(
                "POM "
                        + input.rawPom().artifactId()
                        + " for "
                        + input.requestedCoordinate().groupId()
                        + ":"
                        + input.requestedCoordinate().artifactId()
                        + " must declare or inherit a version."));
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
