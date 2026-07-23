package sh.zolt.update;

import java.util.List;

/**
 * A collected surface awaiting discovery: what to report it as, its current version, and the
 * coordinate(s) to query. {@code intersect} is true only for a version alias, whose candidate
 * listing is the intersection of every governed coordinate's versions.
 */
record SurfaceRequest(
        OutdatedSurface surface,
        String identifier,
        String section,
        String currentVersion,
        List<DiscoveryCoordinate> queryCoordinates,
        boolean intersect,
        List<String> governs) {
}
