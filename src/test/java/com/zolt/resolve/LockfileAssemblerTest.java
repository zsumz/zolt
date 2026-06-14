package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cache.CachedArtifact;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParser;
import com.zolt.project.ProjectConfig;
import com.zolt.toml.ZoltTomlParser;
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
