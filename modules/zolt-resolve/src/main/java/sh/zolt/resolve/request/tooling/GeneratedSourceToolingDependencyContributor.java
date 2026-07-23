package sh.zolt.resolve.request.tooling;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.CoordinateParser;
import sh.zolt.project.ExecToolCoordinate;
import sh.zolt.project.GeneratedSourceKind;
import sh.zolt.project.GeneratedSourceStep;
import sh.zolt.project.OpenApiGenerationSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProtobufGenerationSettings;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.request.RequestOrigin;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class GeneratedSourceToolingDependencyContributor {
    private final CoordinateParser coordinateParser;

    public GeneratedSourceToolingDependencyContributor(CoordinateParser coordinateParser) {
        this.coordinateParser = coordinateParser;
    }

    public void contribute(ProjectConfig config, List<DependencyRequest> requests) {
        addOpenApiToolRequests(config, requests);
        addProtobufToolRequests(config, requests);
        addExecToolRequests(config, requests);
    }

    private void addExecToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        for (GeneratedSourceStep step : execSteps(config)) {
            for (ExecToolCoordinate coordinate : step.exec().tool().coordinates()) {
                String version = coordinate.version()
                        .filter(value -> !value.isBlank())
                        .orElseThrow(() -> new ResolveException(
                                "Exec tool `" + step.exec().toolName() + "` coordinate " + coordinate.coordinate()
                                        + " requires a version. Add version or versionRef, run `zolt resolve`, then retry."));
                addToolRequest(requests, coordinate.coordinate(), version, DependencyScope.TOOL_EXEC);
            }
        }
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
        addToolRequest(requests, coordinate, version, DependencyScope.TOOL_OPENAPI);
    }

    private void addProtobufToolRequests(
            ProjectConfig config,
            List<DependencyRequest> requests) {
        List<GeneratedSourceStep> steps = protobufSteps(config);
        if (steps.isEmpty()) {
            return;
        }
        ProtobufGenerationSettings settings = steps.getFirst().protobuf();
        addProtobufToolRequest(
                requests,
                "Protobuf generation requires [generated.protobufTool].protocCoordinate.",
                "Protobuf generation requires [generated.protobufTool].protocVersion",
                settings.protocCoordinate(),
                settings.protocVersion());
        if (settings.grpc()) {
            addProtobufToolRequest(
                    requests,
                    "Protobuf gRPC generation requires [generated.protobufTool].grpcPluginCoordinate.",
                    "Protobuf gRPC generation requires [generated.protobufTool].grpcPluginVersion",
                    settings.grpcPluginCoordinate(),
                    settings.grpcPluginVersion());
        }
    }

    private void addProtobufToolRequest(
            List<DependencyRequest> requests,
            String missingCoordinateMessage,
            String missingVersionMessage,
            Optional<String> coordinateValue,
            Optional<String> versionValue) {
        String coordinate = coordinateValue
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        missingCoordinateMessage
                                + " Add [generated.protobufTool] versions, run `zolt resolve`, then retry."));
        String version = versionValue
                .filter(value -> !value.isBlank())
                .orElseThrow(() -> new ResolveException(
                        missingVersionMessage
                                + " for "
                                + coordinate
                                + ". Add version or versionRef, run `zolt resolve`, then retry."));
        addToolRequest(requests, coordinate, version, DependencyScope.TOOL_PROTOBUF);
    }

    private void addToolRequest(
            List<DependencyRequest> requests,
            String coordinate,
            String version,
            DependencyScope scope) {
        Coordinate parsed = coordinateParser.parse(coordinate + ":" + version);
        PackageId packageId = PackageId.from(parsed);
        boolean alreadyRequested = requests.stream()
                .anyMatch(request -> request.packageId().equals(packageId)
                        && request.scope() == scope);
        if (alreadyRequested) {
            return;
        }
        requests.add(new DependencyRequest(
                packageId,
                parsed.version().orElseThrow(),
                scope,
                RequestOrigin.DIRECT));
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

    private static List<GeneratedSourceStep> protobufSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.PROTOBUF)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.PROTOBUF)
                .forEach(steps::add);
        return steps;
    }

    private static List<GeneratedSourceStep> execSteps(ProjectConfig config) {
        List<GeneratedSourceStep> steps = new ArrayList<>();
        config.build().generatedMainSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .forEach(steps::add);
        config.build().generatedTestSources().stream()
                .filter(step -> step.kind() == GeneratedSourceKind.EXEC)
                .forEach(steps::add);
        return steps;
    }
}
