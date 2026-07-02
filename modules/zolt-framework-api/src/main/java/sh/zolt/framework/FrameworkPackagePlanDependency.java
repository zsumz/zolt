package sh.zolt.framework;

import sh.zolt.dependency.DependencyScope;
import java.util.List;

public record FrameworkPackagePlanDependency(
        String coordinate,
        String version,
        DependencyScope scope,
        List<String> lanes,
        boolean packageDefault,
        String laneDisposition,
        String disposition,
        String ruleName,
        String location,
        String reason,
        List<String> policies) {
    public FrameworkPackagePlanDependency {
        if (coordinate == null || coordinate.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency coordinate is required.");
        }
        if (version == null || version.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency version is required.");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Framework package plan dependency scope is required.");
        }
        lanes = lanes == null ? List.of() : List.copyOf(lanes);
        if (laneDisposition == null || laneDisposition.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency lane disposition is required.");
        }
        if (disposition == null || disposition.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency disposition is required.");
        }
        if (ruleName == null || ruleName.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency rule name is required.");
        }
        location = location == null ? "" : location;
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Framework package plan dependency reason is required.");
        }
        policies = policies == null ? List.of() : List.copyOf(policies);
    }

    public FrameworkPackagePlanDependency(
            String coordinate,
            String version,
            DependencyScope scope,
            String disposition,
            String ruleName,
            String location,
            String reason,
            List<String> policies) {
        this(
                coordinate,
                version,
                scope,
                sh.zolt.classpath.ClasspathLanePolicy.lanes(scope),
                scope.packagedByDefault(),
                sh.zolt.classpath.ClasspathLanePolicy.disposition(scope),
                disposition,
                ruleName,
                location,
                reason,
                policies);
    }
}
