package com.zolt.explain.maven;

import static com.zolt.explain.maven.MavenXml.child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.w3c.dom.Element;

/**
 * Resolves the effective Maven {@code <dependencyManagement>} for a module by accumulating its reactor
 * parent chain root-first (a nearer POM overrides a farther one, mirroring Maven's inheritance), then
 * fills a version onto the module's version-less {@code <dependencies>}. This is what lets a child pick
 * up a version managed only by its reactor parent instead of emitting an empty version.
 *
 * <p>Managed entries whose version is blank or still contains an unresolved {@code ${...}} are dropped
 * so they cannot mask a version-less dependency with a non-version; an external/unresolved parent simply
 * contributes nothing and the child keeps its honest version-less dependency.
 */
final class MavenManagedVersions {
    private final Map<String, String> versionsByKey;

    private MavenManagedVersions(Map<String, String> versionsByKey) {
        this.versionsByKey = versionsByKey;
    }

    /**
     * Builds the effective managed versions keyed by {@code groupId:artifactId:type} for {@code project},
     * layering ancestors (nearest-parent-first) root-first so nearer declarations win on collision.
     */
    static MavenManagedVersions resolve(
            Element project,
            List<Element> ancestors,
            MavenPomProperties properties) {
        Map<String, String> managed = new LinkedHashMap<>();
        List<Element> rootFirst = new ArrayList<>(ancestors);
        Collections.reverse(rootFirst);
        rootFirst.add(project);
        for (Element pom : rootFirst) {
            for (MavenDependencyInspection managedDependency : managedDependencies(pom, properties)) {
                String version = managedDependency.version();
                if (version.isBlank() || version.contains("${")) {
                    continue;
                }
                managed.put(key(managedDependency), version);
            }
        }
        return new MavenManagedVersions(managed);
    }

    /** Returns {@code dependencies} with an inherited version filled onto each version-less entry. */
    List<MavenDependencyInspection> applyTo(List<MavenDependencyInspection> dependencies) {
        if (versionsByKey.isEmpty()) {
            return dependencies;
        }
        List<MavenDependencyInspection> resolved = new ArrayList<>(dependencies.size());
        for (MavenDependencyInspection dependency : dependencies) {
            String managedVersion = versionsByKey.get(key(dependency));
            if (!dependency.version().isBlank() || managedVersion == null) {
                resolved.add(dependency);
                continue;
            }
            resolved.add(new MavenDependencyInspection(
                    dependency.scope(),
                    dependency.coordinate() + ":" + managedVersion,
                    managedVersion,
                    dependency.type(),
                    dependency.optional(),
                    dependency.managed(),
                    dependency.importedBom(),
                    dependency.exclusions()));
        }
        return resolved;
    }

    private static List<MavenDependencyInspection> managedDependencies(
            Element pom,
            MavenPomProperties properties) {
        return child(pom, "dependencyManagement")
                .flatMap(element -> child(element, "dependencies"))
                .map(element -> MavenDependencyParser.parseDependencies(Optional.of(element), true, properties))
                .orElseGet(List::of);
    }

    private static String key(MavenDependencyInspection dependency) {
        String[] parts = dependency.coordinate().split(":");
        String groupId = parts.length > 0 ? parts[0] : "";
        String artifactId = parts.length > 1 ? parts[1] : "";
        return groupId + ":" + artifactId + ":" + dependency.type();
    }
}
