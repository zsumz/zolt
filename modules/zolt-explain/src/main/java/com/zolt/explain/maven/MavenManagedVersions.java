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
 * fills managed facts onto the module's {@code <dependencies>}. This is what lets a child pick up a
 * version or scope managed only by its reactor parent instead of emitting an empty version or the wrong
 * default scope.
 *
 * <p>Managed entries whose version is blank or still contains an unresolved {@code ${...}} are dropped
 * as version sources so they cannot mask a version-less dependency with a non-version. A declared managed
 * scope can still apply without a usable managed version; an external/unresolved parent simply contributes
 * nothing and the child keeps its honest version-less dependency.
 */
final class MavenManagedVersions {
    private final Map<String, ManagedDependency> managedByKey;

    private MavenManagedVersions(Map<String, ManagedDependency> managedByKey) {
        this.managedByKey = managedByKey;
    }

    /**
     * Builds the effective managed versions keyed by {@code groupId:artifactId:type} for {@code project},
     * layering ancestors (nearest-parent-first) root-first so nearer declarations win on collision.
     */
    static MavenManagedVersions resolve(
            Element project,
            List<Element> ancestors,
            MavenPomProperties properties) {
        Map<String, ManagedDependency> managed = new LinkedHashMap<>();
        List<Element> rootFirst = new ArrayList<>(ancestors);
        Collections.reverse(rootFirst);
        rootFirst.add(project);
        for (Element pom : rootFirst) {
            for (MavenDependencyInspection managedDependency : managedDependencies(pom, properties)) {
                String key = key(managedDependency);
                ManagedDependency inherited = managed.get(key);
                String version = usableVersion(managedDependency)
                        ? managedDependency.version()
                        : inherited == null ? "" : inherited.version();
                String scope = usableScope(managedDependency)
                        ? managedDependency.scope()
                        : inherited == null ? "" : inherited.scope();
                if (version.isBlank() && scope.isBlank()) {
                    continue;
                }
                managed.put(key, new ManagedDependency(version, scope));
            }
        }
        return new MavenManagedVersions(managed);
    }

    /**
     * Returns {@code dependencies} with inherited managed facts applied: version for version-less
     * entries, and scope only when the local dependency omitted {@code <scope>}.
     */
    List<MavenDependencyInspection> applyTo(List<MavenDependencyInspection> dependencies) {
        if (managedByKey.isEmpty()) {
            return dependencies;
        }
        List<MavenDependencyInspection> resolved = new ArrayList<>(dependencies.size());
        for (MavenDependencyInspection dependency : dependencies) {
            ManagedDependency managed = managedByKey.get(key(dependency));
            if (managed == null) {
                resolved.add(dependency);
                continue;
            }
            String version = dependency.version().isBlank() && !managed.version().isBlank()
                    ? managed.version()
                    : dependency.version();
            String scope = !dependency.scopeDeclared() && !managed.scope().isBlank()
                    ? managed.scope()
                    : dependency.scope();
            if (version.equals(dependency.version()) && scope.equals(dependency.scope())) {
                resolved.add(dependency);
                continue;
            }
            resolved.add(new MavenDependencyInspection(
                    scope,
                    coordinate(dependency, version),
                    version,
                    dependency.type(),
                    dependency.optional(),
                    dependency.managed(),
                    dependency.importedBom(),
                    dependency.scopeDeclared(),
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

    private static boolean usableVersion(MavenDependencyInspection dependency) {
        return !dependency.version().isBlank() && !dependency.version().contains("${");
    }

    private static boolean usableScope(MavenDependencyInspection dependency) {
        return dependency.scopeDeclared()
                && !dependency.scope().isBlank()
                && !dependency.scope().contains("${");
    }

    private static String coordinate(MavenDependencyInspection dependency, String version) {
        if (!dependency.version().isBlank() || version.isBlank()) {
            return dependency.coordinate();
        }
        return dependency.coordinate() + ":" + version;
    }

    private record ManagedDependency(String version, String scope) {}
}
