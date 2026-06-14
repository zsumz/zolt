package com.zolt.resolve;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.GeneratedSourceKind;
import com.zolt.project.GeneratedSourceStep;
import com.zolt.project.OpenApiGenerationSettings;
import com.zolt.project.PackageMode;
import com.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ToolingDependencyContributor {
    private static final PackageId SPRING_BOOT_LOADER_PACKAGE = new PackageId(
            "org.springframework.boot",
            "spring-boot-loader");
    private static final PackageId JUNIT_PLATFORM_CONSOLE_PACKAGE = new PackageId(
            "org.junit.platform",
            "junit-platform-console");
    private static final String JUNIT_PLATFORM_CONSOLE_VERSION = "1.11.4";
    private static final PackageId JACOCO_AGENT_PACKAGE = new PackageId(
            "org.jacoco",
            "org.jacoco.agent");
    private static final PackageId JACOCO_CLI_PACKAGE = new PackageId(
            "org.jacoco",
            "org.jacoco.cli");
    private static final String JACOCO_VERSION = "0.8.14";

    private final CoordinateParser coordinateParser;

    ToolingDependencyContributor(CoordinateParser coordinateParser) {
        this.coordinateParser = coordinateParser;
    }

    void contribute(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests,
            boolean includeCoverageTooling) {
        addTestToolRequests(config, projectManagedVersions, requests);
        addPackageModeRequests(config, projectManagedVersions, requests);
        addOpenApiToolRequests(config, requests);
        if (includeCoverageTooling) {
            addCoverageToolRequests(config, requests);
        }
    }

    private void addTestToolRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (!hasTestInputs(config)) {
            return;
        }
        boolean consoleAlreadyOnTestClasspath = requests.stream()
                .anyMatch(request -> request.packageId().groupId().equals("org.junit.platform")
                        && request.packageId().artifactId().startsWith("junit-platform-console")
                        && request.scope().entersTestClasspath());
        if (consoleAlreadyOnTestClasspath) {
            return;
        }
        String version = projectManagedVersions.getOrDefault(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                JUNIT_PLATFORM_CONSOLE_VERSION);
        if (version == null || version.isBlank()) {
            version = JUNIT_PLATFORM_CONSOLE_VERSION;
        }
        requests.add(new DependencyRequest(
                JUNIT_PLATFORM_CONSOLE_PACKAGE,
                version,
                DependencyScope.TEST,
                RequestOrigin.TRANSITIVE));
    }

    private void addPackageModeRequests(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests) {
        if (!isSpringBootArchive(config.packageSettings().mode())) {
            return;
        }
        boolean loaderAlreadyOnMainRuntimeClasspath = requests.stream()
                .anyMatch(request -> request.packageId().equals(SPRING_BOOT_LOADER_PACKAGE)
                        && request.scope().entersMainRuntimeClasspath());
        if (loaderAlreadyOnMainRuntimeClasspath) {
            return;
        }
        String version = projectManagedVersions.get(SPRING_BOOT_LOADER_PACKAGE);
        if (version == null || version.isBlank()) {
            throw new ResolveException(
                    "Spring Boot package mode requires package tool artifact `org.springframework.boot:spring-boot-loader`, "
                            + "but no declared [platforms] entry manages its version. Add the Spring Boot platform to [platforms] "
                            + "or declare `org.springframework.boot:spring-boot-loader` with an explicit version, then run `zolt resolve`.");
        }
        requests.add(new DependencyRequest(
                SPRING_BOOT_LOADER_PACKAGE,
                version,
                DependencyScope.RUNTIME,
                RequestOrigin.TRANSITIVE));
    }

    private void addOpenApiToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        List<GeneratedSourceStep> steps = openApiSteps(config);
        if (steps.isEmpty()) {
            return;
        }
        OpenApiGenerationSettings settings = steps.getFirst().openApi();
        String coordinate = settings.toolCoordinate()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        "OpenAPI generation requires [generated.openapiTool].coordinate. "
                                + "Add org.openapitools:openapi-generator-cli with version or versionRef, run `zolt resolve`, then retry."));
        String version = settings.toolVersion()
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        "OpenAPI generation requires [generated.openapiTool].version for "
                                + coordinate
                                + ". Add version or versionRef, run `zolt resolve`, then retry."));
        Coordinate parsed = coordinateParser.parse(coordinate + ":" + version);
        PackageId packageId = PackageId.from(parsed);
        boolean alreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(packageId)
                        && request.scope() == DependencyScope.TOOL_OPENAPI);
        if (alreadyRequested) {
            return;
        }
        requests.add(new DependencyRequest(
                packageId,
                parsed.version().orElseThrow(),
                DependencyScope.TOOL_OPENAPI,
                RequestOrigin.DIRECT));
    }

    private void addCoverageToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        if (!hasTestInputs(config)) {
            return;
        }
        boolean agentAlreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(JACOCO_AGENT_PACKAGE)
                        && request.scope() == DependencyScope.TOOL_COVERAGE);
        if (!agentAlreadyRequested) {
            requests.add(new DependencyRequest(
                    JACOCO_AGENT_PACKAGE,
                    JACOCO_VERSION,
                    DependencyScope.TOOL_COVERAGE,
                    RequestOrigin.TRANSITIVE,
                    Optional.of(ArtifactDescriptor.jar(
                            new Coordinate(
                                    JACOCO_AGENT_PACKAGE.groupId(),
                                    JACOCO_AGENT_PACKAGE.artifactId(),
                                    Optional.of(JACOCO_VERSION)),
                            Optional.of("runtime")))));
        }
        boolean cliAlreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(JACOCO_CLI_PACKAGE)
                        && request.scope() == DependencyScope.TOOL_COVERAGE);
        if (!cliAlreadyRequested) {
            requests.add(new DependencyRequest(
                    JACOCO_CLI_PACKAGE,
                    JACOCO_VERSION,
                    DependencyScope.TOOL_COVERAGE,
                    RequestOrigin.TRANSITIVE));
        }
    }

    private static boolean hasTestInputs(ProjectConfig config) {
        return !config.testDependencies().isEmpty()
                || !config.managedTestDependencies().isEmpty()
                || !config.workspaceTestDependencies().isEmpty()
                || !config.testAnnotationProcessors().isEmpty()
                || !config.managedTestAnnotationProcessors().isEmpty();
    }

    private static boolean isSpringBootArchive(PackageMode mode) {
        return mode == PackageMode.SPRING_BOOT || mode == PackageMode.SPRING_BOOT_WAR;
    }

    private static List<GeneratedSourceStep> openApiSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.OPENAPI)
                .forEach(steps::add);
        return steps;
    }
}
