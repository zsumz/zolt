package sh.zolt.update;

import sh.zolt.maven.metadata.MetadataDiscovery;
import sh.zolt.maven.metadata.VersionDiscovery;
import sh.zolt.maven.repository.RepositoryAccess;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** In-memory {@link VersionDiscovery} for engine tests: canned listings keyed by group:artifact. */
final class FakeVersionDiscovery implements VersionDiscovery {
    private final Map<String, MetadataDiscovery> byCoordinate = new LinkedHashMap<>();

    FakeVersionDiscovery listing(String coordinate, String source, String... versions) {
        Map<String, String> sourceByVersion = new LinkedHashMap<>();
        for (String version : versions) {
            sourceByVersion.putIfAbsent(version, source);
        }
        byCoordinate.put(coordinate, new MetadataDiscovery(true, List.of(versions), sourceByVersion, List.of()));
        return this;
    }

    FakeVersionDiscovery note(String coordinate, String note) {
        byCoordinate.put(coordinate, new MetadataDiscovery(false, List.of(), Map.of(), List.of(note)));
        return this;
    }

    @Override
    public MetadataDiscovery discover(
            List<RepositoryAccess> repositories, String groupId, String artifactId, boolean offline) {
        return byCoordinate.getOrDefault(
                groupId + ":" + artifactId, new MetadataDiscovery(false, List.of(), Map.of(), List.of()));
    }
}
