package sh.zolt.resolve.request;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.CoordinateParser;
import sh.zolt.project.DependencyExclusionSpec;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionPolicy;
import sh.zolt.resolve.ResolveException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DirectDependencyRequestPlanner {
    private final CoordinateParser coordinateParser;

    DirectDependencyRequestPlanner(CoordinateParser coordinateParser) {
        this.coordinateParser = coordinateParser;
    }

    List<DependencyRequest> plan(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions) {
        return plan(config, projectManagedVersions, "zolt resolve");
    }

    List<DependencyRequest> plan(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            String retryCommand) {
        List<DependencyRequest> requests = new ArrayList<>();
        addDirectRequests(
                requests,
                config,
                "api.dependencies",
                config.apiDependencies(),
                config.managedApiDependencies(),
                projectManagedVersions,
                DependencyScope.COMPILE,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "dependencies",
                config.dependencies(),
                config.managedDependencies(),
                projectManagedVersions,
                DependencyScope.COMPILE,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "runtime.dependencies",
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                projectManagedVersions,
                DependencyScope.RUNTIME,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "provided.dependencies",
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                projectManagedVersions,
                DependencyScope.PROVIDED,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "dev.dependencies",
                config.devDependencies(),
                config.managedDevDependencies(),
                projectManagedVersions,
                DependencyScope.DEV,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "test.dependencies",
                config.testDependencies(),
                config.managedTestDependencies(),
                projectManagedVersions,
                DependencyScope.TEST,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "annotationProcessors",
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                projectManagedVersions,
                DependencyScope.PROCESSOR,
                retryCommand);
        addDirectRequests(
                requests,
                config,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                projectManagedVersions,
                DependencyScope.TEST_PROCESSOR,
                retryCommand);
        return requests;
    }

    private void addDirectRequests(
            List<DependencyRequest> requests,
            ProjectConfig config,
            String section,
            Map<String, String> dependencies,
            Iterable<String> managedDependencies,
            Map<PackageId, String> projectManagedVersions,
            DependencyScope scope,
            String retryCommand) {
        for (Map.Entry<String, String> dependency : dependencies.entrySet()) {
            Coordinate coordinate = coordinateParser.parse(dependency.getKey() + ":" + dependency.getValue());
            requests.add(directDependencyRequest(
                    config,
                    section,
                    PackageId.from(coordinate),
                    coordinate.version().orElseThrow(),
                    scope,
                    retryCommand));
        }
        for (String dependency : managedDependencies) {
            Coordinate coordinate = coordinateParser.parse(dependency);
            PackageId packageId = PackageId.from(coordinate);
            requests.add(directDependencyRequest(
                    config,
                    section,
                    packageId,
                    managedVersion(section, packageId, projectManagedVersions),
                    scope,
                    retryCommand));
        }
    }

    private static DependencyRequest directDependencyRequest(
            ProjectConfig config,
            String section,
            PackageId packageId,
            String version,
            DependencyScope scope,
            String retryCommand) {
        validateSupportedVersion(section, packageId, version, retryCommand);
        DependencyMetadata metadata = config.dependencyMetadata()
                .get(DependencyMetadata.key(section, packageId.toString()));
        Optional<ArtifactDescriptor> artifactDescriptor = directArtifactDescriptor(packageId, version, metadata);
        if (metadata == null || metadata.exclusions().isEmpty()) {
            return new DependencyRequest(packageId, version, scope, RequestOrigin.DIRECT, artifactDescriptor);
        }
        return new DependencyRequest(
                packageId,
                version,
                scope,
                RequestOrigin.DIRECT,
                artifactDescriptor,
                metadata.exclusions().stream()
                        .map(exclusion -> directExclusion(exclusion, retryCommand))
                        .toList());
    }

    private static Optional<ArtifactDescriptor> directArtifactDescriptor(
            PackageId packageId,
            String version,
            DependencyMetadata metadata) {
        if (metadata == null) {
            return Optional.empty();
        }
        String extension = metadata.type() == null ? "jar" : metadata.type();
        if (metadata.classifier() == null && "jar".equals(extension)) {
            return Optional.empty();
        }
        Coordinate coordinate = new Coordinate(packageId.groupId(), packageId.artifactId(), Optional.of(version));
        return Optional.of(new ArtifactDescriptor(coordinate, Optional.ofNullable(metadata.classifier()), extension));
    }

    private static void validateSupportedVersion(
            String section,
            PackageId packageId,
            String version,
            String retryCommand) {
        VersionPolicy.violation(VersionPolicy.Context.EXTERNAL_DEPENDENCY, version).ifPresent(violation -> {
            throw ResolveException.actionable(
                    "Unsupported external dependency version `"
                            + version
                            + "` for `"
                            + packageId
                            + "` in ["
                            + section
                            + "].",
                    violation.guidance() + " Use a fixed released version, then run `"
                            + retryCommand
                            + "` again.");
        });
    }

    private static DependencyExclusion directExclusion(DependencyExclusionSpec exclusion, String retryCommand) {
        if ("*".equals(exclusion.group()) || "*".equals(exclusion.artifact())) {
            throw ResolveException.actionable(
                    "Wildcard dependency exclusions are not supported in zolt.toml: "
                            + exclusion.group()
                            + ":"
                            + exclusion.artifact()
                            + ".",
                    "Replace it with explicit group and artifact exclusions, then run `"
                            + retryCommand
                            + "` again.");
        }
        return new DependencyExclusion(exclusion.group(), exclusion.artifact());
    }

    private static String managedVersion(
            String section,
            PackageId packageId,
            Map<PackageId, String> projectManagedVersions) {
        String version = projectManagedVersions.get(packageId);
        if (version == null || version.isBlank()) {
            throw ResolveException.actionable(
                    "Dependency "
                            + packageId
                            + " in ["
                            + section
                            + "] uses a platform-managed version, but no declared [platforms] entry manages it.",
                    "Add a version or add a platform that manages this dependency.");
        }
        return version;
    }
}
