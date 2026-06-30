package com.zolt.resolve.request;

import com.zolt.dependency.PackageId;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import java.util.List;
import java.util.Map;

public final class DependencyRequestPlanner {
    private final DirectDependencyRequestPlanner directDependencyRequestPlanner;
    private final ToolingDependencyContributor toolingDependencyContributor;

    public DependencyRequestPlanner(CoordinateParser coordinateParser) {
        this(
                new DirectDependencyRequestPlanner(coordinateParser),
                new ToolingDependencyContributor(coordinateParser));
    }

    DependencyRequestPlanner(
            CoordinateParser coordinateParser,
            ToolingDependencyContributor toolingDependencyContributor) {
        this(new DirectDependencyRequestPlanner(coordinateParser), toolingDependencyContributor);
    }

    DependencyRequestPlanner(
            DirectDependencyRequestPlanner directDependencyRequestPlanner,
            ToolingDependencyContributor toolingDependencyContributor) {
        this.directDependencyRequestPlanner = directDependencyRequestPlanner == null
                ? new DirectDependencyRequestPlanner(new CoordinateParser())
                : directDependencyRequestPlanner;
        this.toolingDependencyContributor = toolingDependencyContributor == null
                ? new ToolingDependencyContributor(new CoordinateParser())
                : toolingDependencyContributor;
    }

    public List<DependencyRequest> plan(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            boolean includeCoverageTooling) {
        List<DependencyRequest> requests = directDependencyRequestPlanner.plan(config, projectManagedVersions);
        toolingDependencyContributor.contribute(config, projectManagedVersions, requests, includeCoverageTooling);
        validateDirectRequestsAllowed(config, requests);
        return List.copyOf(requests);
    }

    private static void validateDirectRequestsAllowed(
            ProjectConfig config,
            List<DependencyRequest> directRequests) {
        List<DependencyPolicyExclusion> exclusions = config.dependencyPolicy().exclusions();
        if (exclusions.isEmpty()) {
            return;
        }
        for (DependencyRequest request : directRequests) {
            for (DependencyPolicyExclusion exclusion : exclusions) {
                if (exclusion.group().equals(request.packageId().groupId())
                        && exclusion.artifact().equals(request.packageId().artifactId())) {
                    String reason = exclusion.reason()
                            .map(value -> " Reason: " + value + ".")
                            .orElse("");
                    throw new ResolveException(
                            "Dependency policy excludes direct dependency `"
                                    + request.packageId()
                                    + "`."
                                    + reason
                                    + " Remove the direct dependency or remove the matching [dependencyPolicy].exclude entry, then run `zolt resolve` again.");
                }
            }
        }
    }
}
