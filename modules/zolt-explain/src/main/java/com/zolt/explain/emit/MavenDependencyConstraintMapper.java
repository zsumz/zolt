package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenDependencyInspection;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyConstraintKind;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

final class MavenDependencyConstraintMapper {
    private MavenDependencyConstraintMapper() {
    }

    static Map<String, DependencyConstraint> map(
            List<MavenDependencyInspection> dependencyManagement,
            List<MavenDependencyInspection> directDependencies,
            List<String> notes) {
        Set<String> directCoordinates = directCoordinates(directDependencies);
        Map<String, DependencyConstraint> constraints = new TreeMap<>();
        for (MavenDependencyInspection dependency : dependencyManagement) {
            String coordinate = coordinateOf(dependency.coordinate());
            if (directCoordinates.contains(coordinate)) {
                continue;
            }
            if (!dependency.classifier().isBlank()) {
                notes.add(
                        "Managed dependency `" + coordinate + "` declares Maven classifier `"
                                + dependency.classifier()
                                + "`. [dependencyConstraints] cannot express classifier-specific artifacts;"
                                + " review it manually.");
                continue;
            }
            if (!"jar".equals(dependency.type())) {
                notes.add(
                        "Managed dependency `" + coordinate + "` uses Maven type `" + dependency.type()
                                + "`, which [dependencyConstraints] cannot express; review it manually.");
                continue;
            }
            if (dependency.version().isBlank()) {
                continue;
            }
            if (dependency.version().contains("${")) {
                notes.add(
                        "Managed dependency `" + coordinate + "` uses version `" + dependency.version()
                                + "`, which references a property the static audit could not resolve."
                                + " Add the matching [dependencyConstraints] entry manually.");
                continue;
            }
            constraints.put(coordinate, new DependencyConstraint(
                    coordinate,
                    dependency.version(),
                    DependencyConstraintKind.STRICT,
                    Optional.of("Imported from Maven dependencyManagement.")));
        }
        return constraints;
    }

    private static Set<String> directCoordinates(List<MavenDependencyInspection> dependencies) {
        Set<String> coordinates = new TreeSet<>();
        for (MavenDependencyInspection dependency : dependencies) {
            coordinates.add(coordinateOf(dependency.coordinate()));
        }
        return coordinates;
    }

    private static String coordinateOf(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return coordinate;
    }
}
