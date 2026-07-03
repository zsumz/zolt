package sh.zolt.resolve.lockfile.assembly;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cache.CachedArtifact;
import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.CoordinateParser;
import sh.zolt.project.ProjectConfig;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.request.RequestOrigin;
import sh.zolt.resolve.graph.ResolutionEdge;
import sh.zolt.resolve.graph.ResolutionGraph;
import sh.zolt.resolve.metadata.platform.ManagedVersion;
import sh.zolt.resolve.traversal.DependencyTraversalDecision;
import sh.zolt.resolve.version.VersionSelectionResult;
import sh.zolt.toml.ZoltTomlParser;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class LockfileAssemblerTest {
    private static final PackageId APP = new PackageId("com.example", "app");
    private static final PackageId LIB = new PackageId("com.example", "lib");
    private static final PackageNode APP_NODE = new PackageNode(APP, "1.0.0");
    private static final PackageNode LIB_NODE = new PackageNode(LIB, "2.0.0");

    private final LockfileAssembler assembler = new LockfileAssembler(new CoordinateParser());

    @Test
    void assemblesPackagesDependenciesPoliciesAndMetrics() {
        DependencyRequest appRequest = new DependencyRequest(APP, "1.0.0", DependencyScope.COMPILE, RequestOrigin.DIRECT);
        DependencyRequest libRequest = new DependencyRequest(LIB, "2.0.0", DependencyScope.COMPILE, RequestOrigin.TRANSITIVE);
        ResolutionGraph graph = new ResolutionGraph(
                List.of(APP_NODE, LIB_NODE),
                List.of(new ResolutionEdge(
                        APP_NODE,
                        LIB_NODE,
                        libRequest,
                        DependencyTraversalDecision.include("test"))),
                List.of());
        FakeAssemblyContext context = new FakeAssemblyContext(configWithManagedDependencyAndAlias());
        context.managedVersions.put(APP, new ManagedVersion("1.0.0", "com.example:platform:1.0.0"));

        ZoltLockfile lockfile = assembler.assemble(
                context,
                graph,
                new VersionSelectionResult(List.of(APP_NODE, LIB_NODE), List.of()),
                List.of(appRequest));

        assertTrue(lockfile.aliasFingerprint().orElseThrow().startsWith("sha256:"));
        assertEquals(2, lockfile.packages().size());
        LockPackage app = lockfile.packages().getFirst();
        assertEquals(APP, app.packageId());
        assertEquals(List.of("com.example:lib:2.0.0"), app.dependencies());
        assertEquals(List.of(
                "managed-version: com.example:app -> 1.0.0 from com.example:platform:1.0.0"),
                app.policies());
        assertEquals("repo", app.source());
        assertTrue(app.jarSha256().isPresent());
        assertTrue(app.pomSha256().isPresent());
        assertTrue(context.lockfileAssemblyNanos > 0);
    }

    @Test
    void preservesClassifierJarDescriptor() {
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                new Coordinate("org.jacoco", "org.jacoco.agent", Optional.of("0.8.14")),
                Optional.of("runtime"),
                "jar");
        DependencyRequest request = new DependencyRequest(
                new PackageId("org.jacoco", "org.jacoco.agent"),
                "0.8.14",
                DependencyScope.TOOL_COVERAGE,
                RequestOrigin.DIRECT,
                Optional.of(descriptor));
        PackageNode node = new PackageNode(request.packageId(), request.requestedVersion());
        FakeAssemblyContext context = new FakeAssemblyContext(baseConfig());

        ZoltLockfile lockfile = assembler.assemble(
                context,
                new ResolutionGraph(List.of(node), List.of(), List.of()),
                new VersionSelectionResult(List.of(node), List.of()),
                List.of(request));

        LockPackage pkg = lockfile.packages().getFirst();
        assertEquals(Optional.of("org/jacoco/org.jacoco.agent/0.8.14/org.jacoco.agent-0.8.14-runtime.jar"), pkg.jar());
        assertTrue(pkg.artifact().isEmpty());
        assertTrue(pkg.artifactType().isEmpty());
    }

    @Test
    void recordsTypedArtifactsOutsideJarFields() {
        PackageId schema = new PackageId("com.example", "schema");
        ArtifactDescriptor descriptor = new ArtifactDescriptor(
                new Coordinate(schema.groupId(), schema.artifactId(), Optional.of("1.0.0")),
                Optional.empty(),
                "properties");
        DependencyRequest request = new DependencyRequest(
                schema,
                "1.0.0",
                DependencyScope.TOOL_OPENAPI,
                RequestOrigin.DIRECT,
                Optional.of(descriptor));
        PackageNode node = new PackageNode(schema, "1.0.0");

        ZoltLockfile lockfile = assembler.assemble(
                new FakeAssemblyContext(baseConfig()),
                new ResolutionGraph(List.of(node), List.of(), List.of()),
                new VersionSelectionResult(List.of(node), List.of()),
                List.of(request));

        LockPackage pkg = lockfile.packages().getFirst();
        assertTrue(pkg.jar().isEmpty());
        assertTrue(pkg.jarSha256().isEmpty());
        assertEquals(Optional.of("com/example/schema/1.0.0/schema-1.0.0.properties"), pkg.artifact());
        assertEquals(Optional.of("properties"), pkg.artifactType());
        assertTrue(pkg.artifactSha256().isPresent());
        assertTrue(pkg.pomSha256().isPresent());
    }

    @Test
    void aliasFingerprintIncludesPolicyAndGeneratedSourceVersionRefsDeterministically() {
        ZoltLockfile first = assembler.assemble(
                new FakeAssemblyContext(aliasFingerprintConfig("api", "fixtures")),
                new ResolutionGraph(List.of(), List.of(), List.of()),
                new VersionSelectionResult(List.of(), List.of()),
                List.of());
        ZoltLockfile second = assembler.assemble(
                new FakeAssemblyContext(aliasFingerprintConfig("fixtures", "api")),
                new ResolutionGraph(List.of(), List.of(), List.of()),
                new VersionSelectionResult(List.of(), List.of()),
                List.of());

        assertTrue(first.aliasFingerprint().orElseThrow().startsWith("sha256:"));
        assertEquals(first.aliasFingerprint(), second.aliasFingerprint());
    }

    private static ProjectConfig configWithManagedDependencyAndAlias() {
        return new ZoltTomlParser().parse("""
                [project]
                name = "demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                app = "1.0.0"

                [dependencies]
                "com.example:app" = {}
                """);
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

    private static ProjectConfig aliasFingerprintConfig(String firstGeneratedStep, String secondGeneratedStep) {
        return new ZoltTomlParser().parse("""
                [project]
                name = "generated-demo"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                openapi = "7.11.0"
                protoc = "4.28.3"
                grpc = "1.68.1"
                tomcat = "10.1.40"

                [dependencyConstraints]
                "org.apache.tomcat.embed:tomcat-embed-core" = { versionRef = "tomcat", kind = "strict" }

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "openapi"

                [generated.protobufTool]
                protocVersionRef = "protoc"
                grpcPluginVersionRef = "grpc"

                %s

                %s
                """.formatted(generatedStep(firstGeneratedStep), generatedStep(secondGeneratedStep)));
    }

    private static String generatedStep(String id) {
        return switch (id) {
            case "api" -> """
                    [generated.test.api]
                    kind = "openapi"
                    language = "java"
                    input = "src/test/openapi/api.yaml"
                    output = "target/generated/test-sources/openapi/api"
                    generator = "spring"
                    """;
            case "fixtures" -> """
                    [generated.test.fixtures]
                    kind = "protobuf"
                    language = "java"
                    inputs = ["src/test/proto/fixtures.proto"]
                    output = "target/generated/test-sources/protobuf"
                    """;
            default -> throw new IllegalArgumentException("Unknown generated step " + id);
        };
    }

    private static final class FakeAssemblyContext implements LockfileAssemblyContext {
        private final ProjectConfig config;
        private final Map<PackageId, ManagedVersion> managedVersions = new LinkedHashMap<>();
        private long lockfileAssemblyNanos;

        FakeAssemblyContext(ProjectConfig config) {
            this.config = config;
        }

        @Override
        public ProjectConfig config() {
            return config;
        }

        @Override
        public Map<ArtifactDescriptor, CachedArtifact> getArtifacts(List<ArtifactDescriptor> descriptors) {
            Map<ArtifactDescriptor, CachedArtifact> artifacts = new LinkedHashMap<>();
            for (ArtifactDescriptor descriptor : descriptors) {
                artifacts.put(descriptor, artifact(descriptor));
            }
            return artifacts;
        }

        @Override
        public CachedArtifact getPom(Coordinate coordinate) {
            return new CachedArtifact(
                    coordinate,
                    repositoryPath(coordinate, Optional.empty(), "pom"),
                    Path.of("cache", coordinate.artifactId() + ".pom"),
                    bytes("pom:" + coordinate));
        }

        @Override
        public String sourceFor(CachedArtifact artifact) {
            return "repo";
        }

        @Override
        public Map<PackageId, ManagedVersion> projectManagedVersionDetails() {
            return managedVersions;
        }

        @Override
        public void addLockfileAssemblyNanos(long nanos) {
            lockfileAssemblyNanos += nanos;
        }

        private static CachedArtifact artifact(ArtifactDescriptor descriptor) {
            return new CachedArtifact(
                    descriptor.coordinate(),
                    repositoryPath(descriptor.coordinate(), descriptor.classifier(), descriptor.extension()),
                    Path.of("cache", descriptor.coordinate().artifactId() + "." + descriptor.extension()),
                    bytes("artifact:" + descriptor));
        }

        private static String repositoryPath(Coordinate coordinate, Optional<String> classifier, String extension) {
            String base = coordinate.groupId().replace('.', '/')
                    + "/"
                    + coordinate.artifactId()
                    + "/"
                    + coordinate.version().orElseThrow()
                    + "/"
                    + coordinate.artifactId()
                    + "-"
                    + coordinate.version().orElseThrow();
            return classifier.map(value -> base + "-" + value).orElse(base) + "." + extension;
        }

        private static byte[] bytes(String value) {
            return value.getBytes(StandardCharsets.UTF_8);
        }
    }
}
