package sh.zolt.explain.verify;

import java.util.Comparator;

/**
 * One resolved artifact on a module's scope classpath, from either Maven or Zolt.
 *
 * <p>The comparison identity is {@link #key()} = {@code group:artifact[:classifier]}. The artifact
 * {@code type} is retained for display but is not part of the identity, and {@code version} is
 * compared separately (matched vs version drift). Classifier defaults to the empty string.
 */
public record ResolvedArtifact(
        String groupId,
        String artifactId,
        String type,
        String classifier,
        String version) {

    public static final Comparator<ResolvedArtifact> ORDER = Comparator
            .comparing(ResolvedArtifact::groupId)
            .thenComparing(ResolvedArtifact::artifactId)
            .thenComparing(ResolvedArtifact::classifier)
            .thenComparing(ResolvedArtifact::type)
            .thenComparing(ResolvedArtifact::version);

    public ResolvedArtifact {
        groupId = groupId == null ? "" : groupId;
        artifactId = artifactId == null ? "" : artifactId;
        type = type == null || type.isBlank() ? "jar" : type;
        classifier = classifier == null ? "" : classifier;
        version = version == null ? "" : version;
    }

    /** Identity used to match a Maven artifact against a Zolt artifact: {@code group:artifact[:classifier]}. */
    public String key() {
        return classifier.isEmpty()
                ? groupId + ":" + artifactId
                : groupId + ":" + artifactId + ":" + classifier;
    }

    /** Human display coordinate: {@code group:artifact[:classifier]:version}. */
    public String coordinate() {
        return classifier.isEmpty()
                ? groupId + ":" + artifactId + ":" + version
                : groupId + ":" + artifactId + ":" + classifier + ":" + version;
    }
}
