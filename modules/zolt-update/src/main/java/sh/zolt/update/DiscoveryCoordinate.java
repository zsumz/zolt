package sh.zolt.update;

import java.util.Optional;

/** The {@code group:artifact} a surface's version listing is discovered under. */
record DiscoveryCoordinate(String groupId, String artifactId) {
    static Optional<DiscoveryCoordinate> of(String coordinate) {
        if (coordinate == null) {
            return Optional.empty();
        }
        int colon = coordinate.indexOf(':');
        if (colon <= 0 || colon >= coordinate.length() - 1) {
            return Optional.empty();
        }
        String groupId = coordinate.substring(0, colon);
        String remainder = coordinate.substring(colon + 1);
        int next = remainder.indexOf(':');
        String artifactId = next < 0 ? remainder : remainder.substring(0, next);
        if (groupId.isBlank() || artifactId.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new DiscoveryCoordinate(groupId, artifactId));
    }

    String coordinate() {
        return groupId + ":" + artifactId;
    }
}
