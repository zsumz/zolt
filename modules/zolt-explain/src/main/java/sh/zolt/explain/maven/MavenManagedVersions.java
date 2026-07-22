package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
 * <p>When {@code --resolve-external-parents} recovers an external parent chain, its managed entries seed
 * this map as the farthest layer (before every reactor ancestor), so a version, scope, or exclusion set
 * managed only by the external parent still applies. Recovered managed exclusions are unioned onto the
 * consuming dependency, mirroring {@code PomDependencyManager} in zolt-repository.
 *
 * <p>Managed entries whose version is blank or still contains an unresolved {@code ${...}} are dropped
 * as version sources so they cannot mask a version-less dependency with a non-version. A declared managed
 * scope can still apply without a usable managed version; an external/unresolved parent simply contributes
 * nothing and the child keeps its honest version-less dependency.
 */
final class MavenManagedVersions {
    private final Map<String, ManagedDependency> managedByKey;
    private final List<MavenDependencyInspection> dependencyManagement;

    private MavenManagedVersions(
            Map<String, ManagedDependency> managedByKey,
            List<MavenDependencyInspection> dependencyManagement) {
        this.managedByKey = managedByKey;
        this.dependencyManagement = List.copyOf(dependencyManagement);
    }

    /**
     * Builds the effective managed versions keyed by {@code groupId:artifactId:type} for {@code project},
     * layering ancestors (nearest-parent-first) root-first so nearer declarations win on collision.
     * {@code recovered} external-parent managed entries seed the map as the farthest layer, so reactor and
     * module declarations still override them; pass {@link List#of()} for the offline audit.
     */
    static MavenManagedVersions resolve(
            Element project,
            List<Element> ancestors,
            MavenPomProperties properties,
            List<RecoveredManagedDependency> recovered) {
        Map<String, ManagedDependency> managed = new LinkedHashMap<>();
        Map<String, MavenDependencyInspection> effective = new LinkedHashMap<>();
        seedRecovered(managed, recovered, properties);
        List<Element> rootFirst = new ArrayList<>(ancestors);
        Collections.reverse(rootFirst);
        rootFirst.add(project);
        for (Element pom : rootFirst) {
            for (MavenDependencyInspection managedDependency : managedDependencies(pom, properties)) {
                String key = key(managedDependency);
                effective.put(key, effectiveDependency(managedDependency, effective.get(key)));
                ManagedDependency inherited = managed.get(key);
                String version = usableVersion(managedDependency)
                        ? managedDependency.version()
                        : inherited == null ? "" : inherited.version();
                String scope = usableScope(managedDependency)
                        ? managedDependency.scope()
                        : inherited == null ? "" : inherited.scope();
                // A reactor/module dependencyManagement entry is the nearer declaration: it replaces any
                // recovered external exclusions for this key (and reactor-declared exclusions stay
                // uncaptured, preserving the offline audit byte-for-byte).
                if (version.isBlank() && scope.isBlank()) {
                    continue;
                }
                managed.put(key, new ManagedDependency(version, scope, List.of()));
            }
        }
        return new MavenManagedVersions(managed, new ArrayList<>(effective.values()));
    }

    List<MavenDependencyInspection> effectiveDependencyManagement() {
        return dependencyManagement;
    }

    /**
     * Returns {@code dependencies} with inherited managed facts applied: version for version-less
     * entries, scope only when the local dependency omitted {@code <scope>}, and any managed exclusions
     * unioned onto the dependency's own.
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
            List<MavenDependencyExclusion> exclusions =
                    unionExclusions(dependency.exclusions(), managed.exclusions());
            if (version.equals(dependency.version())
                    && scope.equals(dependency.scope())
                    && exclusions.equals(dependency.exclusions())) {
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
                    dependency.classifier(),
                    exclusions));
        }
        return resolved;
    }

    private static void seedRecovered(
            Map<String, ManagedDependency> managed,
            List<RecoveredManagedDependency> recovered,
            MavenPomProperties properties) {
        for (RecoveredManagedDependency entry : recovered) {
            String groupId = properties.interpolate(entry.groupId());
            String artifactId = properties.interpolate(entry.artifactId());
            String type = properties.interpolate(entry.type());
            String key = groupId + ":" + artifactId + ":" + (type.isBlank() ? "jar" : type);
            String rawVersion = properties.interpolate(entry.version());
            String rawScope = properties.interpolate(entry.scope());
            ManagedDependency inherited = managed.get(key);
            String version = usable(rawVersion) ? rawVersion : inherited == null ? "" : inherited.version();
            String scope = usable(rawScope) ? rawScope : inherited == null ? "" : inherited.scope();
            List<MavenDependencyExclusion> exclusions = entry.exclusions().isEmpty()
                    ? inherited == null ? List.of() : inherited.exclusions()
                    : interpolateExclusions(entry.exclusions(), properties);
            if (version.isBlank() && scope.isBlank() && exclusions.isEmpty()) {
                continue;
            }
            managed.put(key, new ManagedDependency(version, scope, exclusions));
        }
    }

    private static List<MavenDependencyExclusion> interpolateExclusions(
            List<MavenDependencyExclusion> exclusions, MavenPomProperties properties) {
        List<MavenDependencyExclusion> interpolated = new ArrayList<>();
        for (MavenDependencyExclusion exclusion : exclusions) {
            interpolated.add(new MavenDependencyExclusion(
                    properties.interpolate(exclusion.groupId()),
                    properties.interpolate(exclusion.artifactId())));
        }
        return interpolated;
    }

    private static List<MavenDependencyExclusion> unionExclusions(
            List<MavenDependencyExclusion> requested, List<MavenDependencyExclusion> managed) {
        if (managed.isEmpty()) {
            return requested;
        }
        LinkedHashSet<MavenDependencyExclusion> union = new LinkedHashSet<>(requested);
        union.addAll(managed);
        List<MavenDependencyExclusion> merged = new ArrayList<>(union);
        merged.sort(Comparator
                .comparing(MavenDependencyExclusion::groupId)
                .thenComparing(MavenDependencyExclusion::artifactId));
        return merged;
    }

    private static MavenDependencyInspection effectiveDependency(
            MavenDependencyInspection dependency,
            MavenDependencyInspection inherited) {
        String version = !dependency.version().isBlank()
                ? dependency.version()
                : inherited == null ? "" : inherited.version();
        String scope = dependency.scopeDeclared()
                ? dependency.scope()
                : inherited == null ? dependency.scope() : inherited.scope();
        return new MavenDependencyInspection(
                scope,
                coordinate(dependency, version),
                version,
                dependency.type(),
                dependency.optional(),
                dependency.managed(),
                dependency.importedBom(),
                dependency.scopeDeclared(),
                dependency.classifier(),
                dependency.exclusions());
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
        return usable(dependency.version());
    }

    private static boolean usableScope(MavenDependencyInspection dependency) {
        return dependency.scopeDeclared() && usable(dependency.scope());
    }

    private static boolean usable(String value) {
        return !value.isBlank() && !value.contains("${");
    }

    private static String coordinate(MavenDependencyInspection dependency, String version) {
        if (!dependency.version().isBlank() || version.isBlank()) {
            return dependency.coordinate();
        }
        return dependency.coordinate() + ":" + version;
    }

    private record ManagedDependency(String version, String scope, List<MavenDependencyExclusion> exclusions) {
        private ManagedDependency {
            exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
        }
    }
}
