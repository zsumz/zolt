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

    /**
     * The variant-aware dependency identity used to dedup and sort POM entries: the {@code groupId:artifactId}
     * plus classifier and type when either is set. Two variants of one GA (a plain jar and a classified jar)
     * are distinct dependencies and must not collapse onto one {@code <dependency>}. A plain jar with neither
     * classifier nor type yields exactly {@code groupId:artifactId}, so a POM without variants is unchanged.
     */
    String coordinate() {
        String base = groupId + ":" + artifactId;
        boolean hasClassifier = classifier != null && !classifier.isBlank();
        boolean hasType = type != null && !type.isBlank();
        if (!hasClassifier && !hasType) {
            return base;
        }
        return base + ":" + (hasClassifier ? classifier : "") + ":" + (hasType ? type : "");
    }
}
