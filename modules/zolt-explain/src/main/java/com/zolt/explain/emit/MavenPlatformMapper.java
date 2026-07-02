package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenDependencyInspection;
import com.zolt.explain.maven.MavenProjectInspection;
import com.zolt.project.VersionPolicy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

final class MavenPlatformMapper {
    private MavenPlatformMapper() {
    }

    static MavenPlatformMapping map(
            List<MavenDependencyInspection> importedBoms,
            WorkspaceMemberRegistry registry,
            Map<String, MavenProjectInspection> reactorProjects,
            List<String> notes) {
        Map<String, String> platforms = new TreeMap<>();
        Map<String, String> managedPins = new TreeMap<>();
        List<MavenDependencyInspection> managedDependencies = new ArrayList<>();
        for (MavenDependencyInspection bom : importedBoms) {
            String coordinate = coordinateOf(bom.coordinate());
            MavenProjectInspection reactorBom = reactorProject(coordinate, registry, reactorProjects);
            if (reactorBom != null) {
                mapReactorBom(coordinate, reactorBom, managedPins, managedDependencies, notes);
                continue;
            }
            mapExternalPlatform(bom, coordinate, platforms, notes);
        }
        return new MavenPlatformMapping(platforms, managedPins, managedDependencies);
    }

    private static MavenProjectInspection reactorProject(
            String coordinate,
            WorkspaceMemberRegistry registry,
            Map<String, MavenProjectInspection> reactorProjects) {
        if (registry == null || registry.pathFor(coordinate) == null) {
            return null;
        }
        return reactorProjects.get(coordinate);
    }

    private static void mapReactorBom(
            String bomCoordinate,
            MavenProjectInspection reactorBom,
            Map<String, String> managedPins,
            List<MavenDependencyInspection> managedDependencies,
            List<String> notes) {
        int pinned = 0;
        for (MavenDependencyInspection dependency : reactorBom.dependencyManagement()) {
            String coordinate = coordinateOf(dependency.coordinate());
            if (!isSafeManagedPin(dependency, coordinate, bomCoordinate, notes)) {
                continue;
            }
            managedPins.put(coordinate, dependency.version());
            managedDependencies.add(dependency);
            pinned++;
        }
        notes.add(
                "Reactor-internal BOM `" + bomCoordinate + "` was not emitted under [platforms]; "
                        + pinned + " managed dependency pin(s) were carried into this draft.");
    }

    private static boolean isSafeManagedPin(
            MavenDependencyInspection dependency,
            String coordinate,
            String bomCoordinate,
            List<String> notes) {
        if (!dependency.classifier().isBlank()) {
            notes.add(
                    "Reactor-internal BOM `" + bomCoordinate + "` manages `" + coordinate
                            + "` with classifier `" + dependency.classifier()
                            + "`; review that pin manually.");
            return false;
        }
        if (!"jar".equals(dependency.type())) {
            notes.add(
                    "Reactor-internal BOM `" + bomCoordinate + "` manages `" + coordinate
                            + "` with Maven type `" + dependency.type()
                            + "`; review that pin manually.");
            return false;
        }
        VersionPolicy.violation(VersionPolicy.Context.EXTERNAL_DEPENDENCY, dependency.version())
                .ifPresent(violation -> notes.add(
                        "Reactor-internal BOM `" + bomCoordinate + "` manages `" + coordinate
                                + "` with unsupported version `" + dependency.version() + "` ("
                                + violation.rule() + "); add a released version manually before resolving."));
        return VersionPolicy.isSupported(VersionPolicy.Context.EXTERNAL_DEPENDENCY, dependency.version());
    }

    private static void mapExternalPlatform(
            MavenDependencyInspection bom,
            String coordinate,
            Map<String, String> platforms,
            List<String> notes) {
        VersionPolicy.violation(VersionPolicy.Context.PLATFORM, bom.version()).ifPresentOrElse(
                violation -> notes.add(
                        "Imported BOM `" + coordinate + "` uses unsupported platform version `"
                                + bom.version() + "` (" + violation.rule()
                                + "); add a fixed released version under [platforms] before resolving."),
                () -> platforms.put(coordinate, bom.version()));
    }

    private static String coordinateOf(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return coordinate;
    }
}
