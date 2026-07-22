package sh.zolt.explain.verify;

/**
 * A dependency present on both sides with the same identity ({@code group:artifact[:classifier]}) but
 * a different resolved version. Both versions are reported; the report does not editorialize about
 * which is correct — Zolt uses highest-version-wins mediation while Maven uses nearest-wins, so drift
 * is an expected, factual outcome for some transitive conflicts.
 */
public record VersionDrift(
        String groupId,
        String artifactId,
        String classifier,
        String mavenVersion,
        String zoltVersion) {

    public VersionDrift {
        groupId = groupId == null ? "" : groupId;
        artifactId = artifactId == null ? "" : artifactId;
        classifier = classifier == null ? "" : classifier;
        mavenVersion = mavenVersion == null ? "" : mavenVersion;
        zoltVersion = zoltVersion == null ? "" : zoltVersion;
    }

    /** Identity display: {@code group:artifact[:classifier]}. */
    public String key() {
        return classifier.isEmpty()
                ? groupId + ":" + artifactId
                : groupId + ":" + artifactId + ":" + classifier;
    }
}
