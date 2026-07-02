package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;
import static sh.zolt.explain.maven.MavenXml.children;
import static sh.zolt.explain.maven.MavenXml.text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.w3c.dom.Element;

/**
 * Parses {@code <dependency>} and nested {@code <exclusions>} elements into
 * {@link MavenDependencyInspection}s, interpolating {@code ${...}} property references in coordinates,
 * versions, and exclusion coordinates against the effective POM properties.
 */
final class MavenDependencyParser {
    private MavenDependencyParser() {
    }

    static List<MavenDependencyInspection> parseDependencies(
            Optional<Element> dependenciesElement,
            boolean managed,
            MavenPomProperties properties) {
        if (dependenciesElement.isEmpty()) {
            return List.of();
        }
        List<MavenDependencyInspection> dependencies = new ArrayList<>();
        for (Element dependency : children(dependenciesElement.orElseThrow(), "dependency")) {
            String groupId = properties.interpolate(text(dependency, "groupId").orElse("unknown-group"));
            String artifactId = properties.interpolate(text(dependency, "artifactId").orElse("unknown-artifact"));
            String version = properties.interpolate(text(dependency, "version").orElse(""));
            Optional<String> declaredScope = text(dependency, "scope");
            String scope = declaredScope.orElse("compile");
            String type = text(dependency, "type").orElse("jar");
            String classifier = properties.interpolate(text(dependency, "classifier").orElse(""));
            boolean optional = text(dependency, "optional").map(Boolean::parseBoolean).orElse(false);
            boolean importedBom = managed && "pom".equals(type) && "import".equals(scope);
            dependencies.add(new MavenDependencyInspection(
                    scope,
                    groupId + ":" + artifactId + (version.isBlank() ? "" : ":" + version),
                    version,
                    type,
                    optional,
                    managed,
                    importedBom,
                    declaredScope.isPresent(),
                    classifier,
                    parseExclusions(dependency, properties)));
        }
        dependencies.sort(Comparator
                .comparing(MavenDependencyInspection::scope)
                .thenComparing(MavenDependencyInspection::coordinate));
        return dependencies;
    }

    private static List<MavenDependencyExclusion> parseExclusions(
            Element dependency,
            MavenPomProperties properties) {
        Optional<Element> exclusionsElement = child(dependency, "exclusions");
        if (exclusionsElement.isEmpty()) {
            return List.of();
        }
        List<MavenDependencyExclusion> exclusions = new ArrayList<>();
        for (Element exclusion : children(exclusionsElement.orElseThrow(), "exclusion")) {
            String groupId = properties.interpolate(text(exclusion, "groupId").orElse("*"));
            String artifactId = properties.interpolate(text(exclusion, "artifactId").orElse("*"));
            exclusions.add(new MavenDependencyExclusion(groupId, artifactId));
        }
        exclusions.sort(Comparator
                .comparing(MavenDependencyExclusion::groupId)
                .thenComparing(MavenDependencyExclusion::artifactId));
        return exclusions;
    }
}
