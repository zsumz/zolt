package com.zolt.explain.maven;

import static com.zolt.explain.maven.MavenXml.child;
import static com.zolt.explain.maven.MavenXml.text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.w3c.dom.Element;

/**
 * A registry of the {@code <project>} elements for every POM reachable within the reactor being
 * inspected, keyed by {@code groupId:artifactId:version} coordinate. {@link MavenStaticProjectInspector}
 * registers every readable reactor POM before building inspections, so a root project can still inherit
 * from a parent POM that lives in a subdirectory module.
 *
 * <p>This lets the inspection builder walk a module's {@code <parent>} chain <em>within the reactor</em>
 * to compute its effective {@code <properties>} and {@code <dependencyManagement>}, mirroring Maven's own
 * inheritance. The walk is bounded to POMs on disk: an external or unresolvable parent simply ends the
 * chain, and the caller keeps the honest version-less review comment rather than fetching over the
 * network.
 */
final class MavenReactorPoms {
    private final Map<String, Element> byCoordinate = new LinkedHashMap<>();
    private final Map<Element, Path> relativePaths = new IdentityHashMap<>();

    /** Registers a POM's {@code <project>} element under its {@code groupId:artifactId:version}. */
    void register(Element project, Path relativePath) {
        relativePaths.put(project, relativePath);
        coordinate(project).ifPresent(coordinate -> byCoordinate.putIfAbsent(coordinate, project));
    }

    Path relativePath(Element project) {
        return relativePaths.getOrDefault(project, Path.of(""));
    }

    /**
     * The ancestor {@code <project>} elements of {@code project}, nearest parent first, resolved by
     * matching each {@code <parent>} coordinate against the registered reactor POMs. The chain stops at
     * the first ancestor that is not on disk (external parents are not fetched) and is cycle-guarded.
     */
    List<Element> ancestors(Element project) {
        List<Element> ancestors = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        coordinate(project).ifPresent(seen::add);
        Optional<Element> current = parent(project);
        while (current.isPresent()) {
            Element ancestor = current.orElseThrow();
            Optional<String> coordinate = coordinate(ancestor);
            if (coordinate.isPresent() && !seen.add(coordinate.orElseThrow())) {
                break;
            }
            ancestors.add(ancestor);
            current = parent(ancestor);
        }
        return ancestors;
    }

    private Optional<Element> parent(Element project) {
        return child(project, "parent").flatMap(parent -> {
            String groupId = text(parent, "groupId").orElse(null);
            String artifactId = text(parent, "artifactId").orElse(null);
            String version = text(parent, "version").orElse(null);
            if (groupId == null || artifactId == null || version == null) {
                return Optional.empty();
            }
            return Optional.ofNullable(byCoordinate.get(coordinate(groupId, artifactId, version)));
        });
    }

    boolean sameCoordinate(Element project, String groupId, String artifactId, String version) {
        return coordinate(project)
                .map(coordinate -> coordinate.equals(coordinate(groupId, artifactId, version)))
                .orElse(false);
    }

    private static Optional<String> coordinate(Element project) {
        Optional<Element> parent = child(project, "parent");
        String groupId = text(project, "groupId")
                .or(() -> parent.flatMap(element -> text(element, "groupId")))
                .orElse(null);
        String artifactId = text(project, "artifactId").orElse(null);
        String version = text(project, "version")
                .or(() -> parent.flatMap(element -> text(element, "version")))
                .orElse(null);
        if (groupId == null || artifactId == null || version == null) {
            return Optional.empty();
        }
        return Optional.of(coordinate(groupId, artifactId, version));
    }

    private static String coordinate(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }
}
