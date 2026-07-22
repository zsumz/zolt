package sh.zolt.explain.maven;

import java.util.List;

/**
 * A {@code <dependencyManagement>} entry recovered from an external parent chain (or an import-scoped BOM
 * expanded within it). Coordinate parts, version, scope, and exclusions are kept verbatim from the
 * recovered effective POM; any {@code ${...}} references are interpolated by the inspection builder
 * against the merged recovered/reactor/module properties so a nearer override still wins.
 */
public record RecoveredManagedDependency(
        String groupId,
        String artifactId,
        String version,
        String scope,
        String type,
        String classifier,
        List<MavenDependencyExclusion> exclusions) {
    public RecoveredManagedDependency {
        groupId = groupId == null ? "" : groupId;
        artifactId = artifactId == null ? "" : artifactId;
        version = version == null ? "" : version;
        scope = scope == null ? "" : scope;
        type = type == null || type.isBlank() ? "jar" : type;
        classifier = classifier == null ? "" : classifier;
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
}
