package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.maven.CoordinateParser;
import com.zolt.project.PackageMode;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class ToolingDependencyContributorTest {
    private static final PackageId JUNIT_CONSOLE = new PackageId("org.junit.platform", "junit-platform-console");
    private static final PackageId SPRING_BOOT_LOADER = new PackageId("org.springframework.boot", "spring-boot-loader");
    private static final PackageId OPENAPI_GENERATOR = new PackageId("org.openapitools", "openapi-generator-cli");
    private static final PackageId JACOCO_AGENT = new PackageId("org.jacoco", "org.jacoco.agent");
    private static final PackageId JACOCO_CLI = new PackageId("org.jacoco", "org.jacoco.cli");

    private final ToolingDependencyContributor contributor = new ToolingDependencyContributor(new CoordinateParser());

    @Test
    void addsJUnitConsoleToolingForTestInputs() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(
                testDependencyConfig(),
                Map.of(JUNIT_CONSOLE, "1.12.0"),
                requests,
                false);

        DependencyRequest request = onlyRequest(requests, JUNIT_CONSOLE);
        assertEquals("1.12.0", request.requestedVersion());
        assertEquals(DependencyScope.TEST, request.scope());
        assertEquals(RequestOrigin.TRANSITIVE, request.origin());
    }

    @Test
    void addsSpringBootLoaderFromManagedPlatformVersion() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(
                baseConfig().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                Map.of(SPRING_BOOT_LOADER, "4.0.6"),
                requests,
                false);

        DependencyRequest request = onlyRequest(requests, SPRING_BOOT_LOADER);
        assertEquals("4.0.6", request.requestedVersion());
        assertEquals(DependencyScope.RUNTIME, request.scope());
        assertEquals(RequestOrigin.TRANSITIVE, request.origin());
    }

    @Test
    void springBootLoaderReportsMissingManagedVersionClearly() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> contributor.contribute(
                        baseConfig().withPackageSettings(new PackageSettings(PackageMode.SPRING_BOOT)),
                        Map.of(),
                        new ArrayList<>(),
                        false));

        assertTrue(exception.getMessage().contains("Spring Boot package mode requires package tool artifact"));
        assertTrue(exception.getMessage().contains("Add the Spring Boot platform to [platforms]"));
    }

    @Test
    void addsOpenApiToolAsDirectToolRequest() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(openApiConfig(), Map.of(), requests, false);

        DependencyRequest request = onlyRequest(requests, OPENAPI_GENERATOR);
        assertEquals("7.11.0", request.requestedVersion());
        assertEquals(DependencyScope.TOOL_OPENAPI, request.scope());
        assertEquals(RequestOrigin.DIRECT, request.origin());
    }

    @Test
    void addsCoverageToolsOnlyWhenRequestedAndTestsExist() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(testDependencyConfig(), Map.of(), requests, true);

        DependencyRequest agent = onlyRequest(requests, JACOCO_AGENT);
        assertEquals("0.8.14", agent.requestedVersion());
        assertEquals(DependencyScope.TOOL_COVERAGE, agent.scope());
        assertTrue(agent.artifactDescriptor().isPresent());
        assertEquals(Optional.of("runtime"), agent.artifactDescriptor().orElseThrow().classifier());

        DependencyRequest cli = onlyRequest(requests, JACOCO_CLI);
        assertEquals("0.8.14", cli.requestedVersion());
        assertEquals(DependencyScope.TOOL_COVERAGE, cli.scope());
    }

    private static DependencyRequest onlyRequest(List<DependencyRequest> requests, PackageId packageId) {
        return requests.stream()
                .filter(request -> request.packageId().equals(packageId))
                .findFirst()
                .orElseThrow();
    }

    private static ProjectConfig baseConfig() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);
    }

    private static ProjectConfig testDependencyConfig() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [test.dependencies]
                "com.example:app" = "1.0.0"
                """);
    }

    private static ProjectConfig openApiConfig() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);
    }
}
