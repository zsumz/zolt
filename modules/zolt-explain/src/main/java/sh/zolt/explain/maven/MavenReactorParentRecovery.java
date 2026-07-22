package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenXml.child;
import static sh.zolt.explain.maven.MavenXml.children;
import static sh.zolt.explain.maven.MavenXml.text;

import sh.zolt.explain.ExplainSignal;
import sh.zolt.explain.ExplainSignals;
import sh.zolt.maven.Coordinate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.w3c.dom.Element;

/**
 * Decides which external parent each reactor module needs recovered and delegates the fetch to a
 * {@link MavenExternalParentResolver}. The external parent is the {@code <parent>} of the topmost on-disk
 * POM in the module's reactor chain (the module itself when it has no reactor ancestors) — the exact point
 * where {@code MavenReactorPoms} stops walking because the ancestor is not on disk.
 *
 * <p>Repositories offered to the resolver are the HTTPS {@code <repositories>} declared anywhere in the
 * reactor, deduplicated and order-stable; Maven Central is appended by the resolver. SNAPSHOT, dynamic, or
 * incomplete parent coordinates are never fetched — they resolve to an {@link RecoveredParentMetadata#unresolved(String)}
 * review note so the audit stays deterministic. Recovery is memoised per external-parent coordinate so
 * every module inheriting the same parent shares one fetch.
 */
final class MavenReactorParentRecovery {
    private final MavenExternalParentResolver resolver;
    private final List<String> repositoryUrls;
    private final Map<String, RecoveredParentMetadata> byCoordinate = new ConcurrentHashMap<>();

    private MavenReactorParentRecovery(MavenExternalParentResolver resolver, List<String> repositoryUrls) {
        this.resolver = resolver;
        this.repositoryUrls = repositoryUrls;
    }

    static MavenReactorParentRecovery create(MavenExternalParentResolver resolver, List<Element> reactorProjects) {
        return new MavenReactorParentRecovery(resolver, repositoryUrls(reactorProjects));
    }

    RecoveredParentMetadata recover(Element project, MavenReactorPoms reactor) {
        List<Element> ancestors = reactor.ancestors(project);
        Element topmost = ancestors.isEmpty() ? project : ancestors.get(ancestors.size() - 1);
        Optional<Element> parentElement = child(topmost, "parent");
        if (parentElement.isEmpty()) {
            return RecoveredParentMetadata.none();
        }
        Element parent = parentElement.orElseThrow();
        String groupId = text(parent, "groupId").orElse("");
        String artifactId = text(parent, "artifactId").orElse("");
        String version = text(parent, "version").orElse("");
        String coordinate = coordinate(groupId, artifactId, version);
        if (groupId.isBlank() || artifactId.isBlank() || version.isBlank()) {
            return RecoveredParentMetadata.unresolved(
                    "External parent `" + coordinate + "` is missing coordinate parts and cannot be fetched;"
                            + " complete its groupId, artifactId, and version.");
        }
        if (version.contains("${")) {
            return RecoveredParentMetadata.unresolved(
                    "External parent version `" + version + "` could not be resolved statically;"
                            + " set a fixed parent version before recovering inherited metadata.");
        }
        if (version.toUpperCase(Locale.ROOT).contains("SNAPSHOT")) {
            return RecoveredParentMetadata.unresolved(
                    "Remote SNAPSHOT parents are not fetched; publish a released parent version or vendor the"
                            + " inherited Maven settings before relying on the draft.");
        }
        return byCoordinate.computeIfAbsent(coordinate, ignored ->
                resolver.resolve(new Coordinate(groupId, artifactId, Optional.of(version)), repositoryUrls));
    }

    /** The informative signal listing the coordinates and source repositories a successful recovery fetched. */
    static ExplainSignal recoveredSignal(String project, RecoveredParentMetadata recovered) {
        List<String> entries = recovered.fetchedArtifacts().stream()
                .map(artifact -> artifact.coordinate() + " (" + artifact.source() + ")")
                .sorted()
                .toList();
        return ExplainSignals.MAVEN_EXTERNAL_PARENT_RESOLVED.signal(
                project,
                "Recovered external parent metadata for " + entries.size() + " POM(s): "
                        + String.join(", ", entries) + ".");
    }

    private static List<String> repositoryUrls(List<Element> reactorProjects) {
        LinkedHashSet<String> urls = new LinkedHashSet<>();
        for (Element project : reactorProjects) {
            child(project, "repositories").ifPresent(repositories -> {
                for (Element repository : children(repositories, "repository")) {
                    text(repository, "url")
                            .filter(url -> url.startsWith("https://"))
                            .ifPresent(urls::add);
                }
            });
        }
        return List.copyOf(urls);
    }

    private static String coordinate(String groupId, String artifactId, String version) {
        return groupId + ":" + artifactId + ":" + version;
    }
}
