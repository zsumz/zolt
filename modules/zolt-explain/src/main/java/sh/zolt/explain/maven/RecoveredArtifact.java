package sh.zolt.explain.maven;

/**
 * One Maven POM or BOM consulted while recovering an external parent chain, paired with the repository
 * (or local cache) it was served from. This feeds the informative recovery signal so the audit stays
 * honest about which coordinates were fetched over the network.
 */
public record RecoveredArtifact(String coordinate, String source) {
    public RecoveredArtifact {
        coordinate = coordinate == null ? "" : coordinate;
        source = source == null ? "" : source;
    }
}
