package com.zolt.resolve;

import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicyExclusion;
import com.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

final class DependencyRequestPlanner {
    private final CoordinateParser coordinateParser;
    private final ToolingDependencyContributor toolingDependencyContributor;

    DependencyRequestPlanner(
            CoordinateParser coordinateParser,
            ToolingDependencyContributor toolingDependencyContributor) {
        this.coordinateParser = coordinateParser;
        this.toolingDependencyContributor = toolingDependencyContributor == null
                ? new ToolingDependencyContributor(coordinateParser)
                : toolingDependencyContributor;
    }

    List<DependencyRequest> plan(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            boolean includeCoverageTooling) {
        List<DependencyRequest> requests = directRequests(config, projectManagedVersions);
        toolingDependencyContributor.contribute(config, projectManagedVersions, requests, includeCoverageTooling);
        validateDirectRequestsAllowed(config, requests);
        return List.copyOf(requests);
    }

    private List<DependencyRequest> directRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions) {
        List<DependencyRequest> requests = new ArrayList<>();
        addDirectRequests(
                requests,
                config,
                "api.dependencies",
                config.apiDependencies(),
                config.managedApiDependencies(),
                projectManagedVersions,
                DependencyScope.COMPILE);
        addDirectRequests(
                requests,
                config,
                "dependencies",
                config.dependencies(),
                config.managedDependencies(),
                projectManagedVersions,
                DependencyScope.COMPILE);
        addDirectRequests(
                requests,
                config,
                "runtime.dependencies",
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                projectManagedVersions,
                DependencyScope.RUNTIME);
        addDirectRequests(
                requests,
                config,
                "provided.dependencies",
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                projectManagedVersions,
                DependencyScope.PROVIDED);
        addDirectRequests(
                requests,
                config,
                "dev.dependencies",
                config.devDependencies(),
                config.managedDevDependencies(),
                projectManagedVersions,
                DependencyScope.DEV);
        addDirectRequests(
                requests,
                config,
                "test.dependencies",
                config.testDependencies(),
                config.managedTestDependencies(),
                projectManagedVersions,
                DependencyScope.TEST);
        addDirectRequests(
                requests,
                config,
                "annotationProcessors",
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                projectManagedVersions,
                DependencyScope.PROCESSOR);
        addDirectRequests(
                requests,
                config,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                projectManagedVersions,
                DependencyScope.TEST_PROCESSOR);
        return requests;
    }

    private void addDirectRequests(
            List<DependencyRequest> requests,
            ProjectConfig config,
            String section,
            Map<String, String> dependencies,
            Iterable<String> managedDependencies,
            Map<PackageId, String> projectManagedVersions,
            DependencyScope scope) {
        for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    section,
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    scope));
        }
        for (String dependency : managedDependencies) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    section,
                    packageId,
                    managedVersion(section, packageId, projectManagedVersions),
                    scope));
        }
    }

    private static DependencyRequest directDependencyRequest(
            ProjectConfig config,
            String section,
            PackageId packageId,
            String version,
            DependencyScope scope) {
        DependencyMetadata metadata = config.dependencyMetadata()
                .get(DependencyMetadata.key(section, packageId.toString()));
        if (metadata == null || metadata.exclusions().isEmpty()) {
            return new DependencyRequest(packageId, version, scope, RequestOrigin.DIRECT);
        }
        return new DependencyRequest(
                packageId,
                version,
                scope,
                RequestOrigin.DIRECT,
                metadata.exclusions().stream()
                        .map(exclusion -> new DependencyExclusion(exclusion.group(), exclusion.artifact()))
                        .toList());
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

    private static String managedVersion(
            String section,
            PackageId packageId,
            Map<PackageId, String> projectManagedVersions) {
        String version = projectManagedVersions.get(packageId);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Dependency "
                            + packageId
                            + " in ["
                            + section
                            + "] uses a platform-managed version, but no declared [platforms] entry manages it. Add a version or add a platform that manages this dependency.");
        }
        return version;
    }
}
