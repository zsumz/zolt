package sh.zolt.update;

import sh.zolt.maven.metadata.MetadataDiscovery;
import sh.zolt.maven.metadata.VersionDiscovery;
import sh.zolt.maven.repository.RepositoryAccess;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers the candidate version listing for a collected surface. A normal surface queries its one
 * coordinate (union across repositories); a version alias intersects the listings of every governed
 * coordinate so a bump is only offered when it exists for all of them.
 */
final class SurfaceDiscovery {
    private final VersionDiscovery discovery;

    SurfaceDiscovery(VersionDiscovery discovery) {
        this.discovery = discovery;
    }

    MetadataDiscovery discover(
            SurfaceRequest surface,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        if (surface.queryCoordinates().isEmpty()) {
            return new MetadataDiscovery(false, List.of(), Map.of(), List.of());
        }
        if (!surface.intersect()) {
            return discoverCoordinate(surface.queryCoordinates().get(0), repositories, offline, memo);
        }
        return intersect(surface.queryCoordinates(), repositories, offline, memo);
    }

    private MetadataDiscovery intersect(
            List<DiscoveryCoordinate> coordinates,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        List<String> intersection = null;
        Map<String, String> sourceByVersion = new LinkedHashMap<>();
        List<String> notes = new ArrayList<>();
        boolean allResolved = true;
        for (DiscoveryCoordinate coordinate : coordinates) {
            MetadataDiscovery discovered = discoverCoordinate(coordinate, repositories, offline, memo);
            notes.addAll(discovered.notes());
            if (!discovered.resolved()) {
                allResolved = false;
                continue;
            }
            intersection = intersection == null
                    ? new ArrayList<>(discovered.versions())
                    : retain(intersection, discovered.versions());
            discovered.sourceByVersion().forEach(sourceByVersion::putIfAbsent);
        }
        List<String> versions = intersection == null ? List.of() : List.copyOf(intersection);
        return new MetadataDiscovery(allResolved && intersection != null, versions, sourceByVersion, notes);
    }

    private MetadataDiscovery discoverCoordinate(
            DiscoveryCoordinate coordinate,
            List<RepositoryAccess> repositories,
            boolean offline,
            Map<String, MetadataDiscovery> memo) {
        return memo.computeIfAbsent(
                coordinate.coordinate(),
                ignored -> discovery.discover(repositories, coordinate.groupId(), coordinate.artifactId(), offline));
    }

    private static List<String> retain(List<String> current, List<String> candidate) {
        List<String> retained = new ArrayList<>(current);
        retained.retainAll(candidate);
        return retained;
    }
}
