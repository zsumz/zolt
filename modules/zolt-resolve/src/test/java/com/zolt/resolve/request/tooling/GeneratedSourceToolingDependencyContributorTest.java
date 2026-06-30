package com.zolt.resolve.request.tooling;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ResolveException;
import com.zolt.resolve.request.DependencyRequest;
import com.zolt.resolve.request.RequestOrigin;
import com.zolt.toml.ZoltTomlParser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

final class GeneratedSourceToolingDependencyContributorTest {
    private static final PackageId OPENAPI_GENERATOR =
            new PackageId("org.openapitools", "openapi-generator-cli");
    private static final PackageId PROTOC = new PackageId("com.google.protobuf", "protoc");

    private final GeneratedSourceToolingDependencyContributor contributor =
            new GeneratedSourceToolingDependencyContributor(new CoordinateParser());

    @Test
    void addsOpenApiToolAsDirectToolRequest() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(openApiConfig("""
                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"
                """), requests);

        DependencyRequest request = onlyRequest(requests, OPENAPI_GENERATOR);
        assertEquals("7.11.0", request.requestedVersion());
        assertEquals(DependencyScope.TOOL_OPENAPI, request.scope());
        assertEquals(RequestOrigin.DIRECT, request.origin());
    }

    @Test
    void reportsMissingOpenApiToolCoordinateClearly() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> contributor.contribute(openApiConfig("""
                        [generated.openapiTool]
                        version = "7.11.0"
                        """), new ArrayList<>()));

        assertTrue(exception.getMessage().contains("OpenAPI generation requires [generated.openapiTool].coordinate"));
        assertTrue(exception.getMessage().contains("run `zolt resolve`"));
    }

    @Test
    void addsProtobufToolAsDirectToolRequest() {
        List<DependencyRequest> requests = new ArrayList<>();

        contributor.contribute(protobufConfig("""
                [generated.protobufTool]
                protocVersion = "4.28.3"
                grpcPluginVersion = "1.68.1"
                """), requests);

        DependencyRequest request = onlyRequest(requests, PROTOC);
        assertEquals("4.28.3", request.requestedVersion());
        assertEquals(DependencyScope.TOOL_PROTOBUF, request.scope());
        assertEquals(RequestOrigin.DIRECT, request.origin());
    }

    @Test
    void reportsMissingProtobufToolVersionClearly() {
        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> contributor.contribute(protobufConfig("""
                        [generated.protobufTool]
                        protocCoordinate = "com.google.protobuf:protoc"
                        """), new ArrayList<>()));

        assertTrue(exception.getMessage().contains("Protobuf generation requires [generated.protobufTool].protocVersion"));
        assertTrue(exception.getMessage().contains("com.google.protobuf:protoc"));
    }

    @Test
    void doesNotDuplicateExistingToolScopeRequest() {
        List<DependencyRequest> requests = new ArrayList<>();
        requests.add(new DependencyRequest(
                OPENAPI_GENERATOR,
                "7.11.0",
                DependencyScope.TOOL_OPENAPI,
                RequestOrigin.DIRECT));

        contributor.contribute(openApiConfig("""
                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                version = "7.11.0"
                """), requests);

        assertEquals(1, requests.stream()
                .filter(request -> request.packageId().equals(OPENAPI_GENERATOR))
                .count());
    }

    private static DependencyRequest onlyRequest(List<DependencyRequest> requests, PackageId packageId) {
        return requests.stream()
                .filter(request -> request.packageId().equals(packageId))
                .findFirst()
                .orElseThrow();
    }

    private static ProjectConfig openApiConfig(String toolSection) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "openapi-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                %s

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """.formatted(toolSection));
    }

    private static ProjectConfig protobufConfig(String toolSection) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "protobuf-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                %s

                [generated.main.greeter]
                kind = "protobuf"
                language = "java"
                inputs = ["src/main/proto/greeter.proto"]
                output = "target/generated/sources/protobuf"
                """.formatted(toolSection));
    }
}
