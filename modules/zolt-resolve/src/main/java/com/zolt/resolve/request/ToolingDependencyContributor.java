package com.zolt.resolve.request;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.request.tooling.GeneratedSourceToolingDependencyContributor;
import com.zolt.resolve.request.tooling.SpringBootToolingDependencyContributor;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class ToolingDependencyContributor {
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

    private final GeneratedSourceToolingDependencyContributor generatedSourceToolingDependencyContributor;
    private final SpringBootToolingDependencyContributor springBootToolingDependencyContributor;

    ToolingDependencyContributor(CoordinateParser coordinateParser) {
        this(
                coordinateParser,
                new GeneratedSourceToolingDependencyContributor(coordinateParser),
                new SpringBootToolingDependencyContributor());
    }

    ToolingDependencyContributor(
            CoordinateParser coordinateParser,
            GeneratedSourceToolingDependencyContributor generatedSourceToolingDependencyContributor) {
        this(coordinateParser, generatedSourceToolingDependencyContributor, new SpringBootToolingDependencyContributor());
    }

    ToolingDependencyContributor(
            CoordinateParser coordinateParser,
            GeneratedSourceToolingDependencyContributor generatedSourceToolingDependencyContributor,
            SpringBootToolingDependencyContributor springBootToolingDependencyContributor) {
        this.generatedSourceToolingDependencyContributor = generatedSourceToolingDependencyContributor == null
                ? new GeneratedSourceToolingDependencyContributor(coordinateParser)
                : generatedSourceToolingDependencyContributor;
        this.springBootToolingDependencyContributor = springBootToolingDependencyContributor == null
                ? new SpringBootToolingDependencyContributor()
                : springBootToolingDependencyContributor;
    }

    void contribute(
            ProjectConfig config,
            Map<PackageId, String> projectManagedVersions,
            List<DependencyRequest> requests,
            boolean includeCoverageTooling) {
        addTestToolRequests(config, projectManagedVersions, requests);
        springBootToolingDependencyContributor.contribute(config, projectManagedVersions, requests);
        generatedSourceToolingDependencyContributor.contribute(config, requests);
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
}
