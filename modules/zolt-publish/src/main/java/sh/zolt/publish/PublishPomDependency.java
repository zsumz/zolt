package sh.zolt.publish;

import sh.zolt.project.DependencyExclusionSpec;
import java.util.List;

/**
 * A single POM dependency (or {@code <dependencyManagement>} entry) ready to emit. Element fields are
 * rendered in Maven order: classifier after artifactId, type after version.
 */
record PublishPomDependency(
        String groupId,
        String artifactId,
        String classifier,
        String version,
        String type,
        String scope,
        boolean optional,
        List<DependencyExclusionSpec> exclusions) {
    PublishPomDependency {
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    String coordinate() {
        return groupId + ":" + artifactId;
    }
}
