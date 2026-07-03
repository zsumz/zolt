package sh.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
import sh.zolt.resolve.ResolveException;
import sh.zolt.resolve.framework.FrameworkDependencyCandidate;
import sh.zolt.resolve.framework.FrameworkDependencyRequestPlanRequest;
import sh.zolt.resolve.request.DependencyRequest;
import sh.zolt.resolve.request.RequestOrigin;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusDependencyRequestPlannerTest {
    private static final String METADATA_PATH = "META-INF/quarkus-extension.properties";

    @TempDir
    private Path tempDir;

    private final QuarkusDependencyRequestPlanner planner = new QuarkusDependencyRequestPlanner();

    @Test
    void returnsNoRequestsWhenQuarkusIsDisabled() {
        FrameworkDependencyRequestPlanRequest request = new FrameworkDependencyRequestPlanRequest(
                config(false),
                List.of(candidate("io.quarkus", "quarkus-rest", "3.33.2", DependencyScope.COMPILE)),
                Map.of(),
                Map.of(),
                coordinate -> {
                    throw new AssertionError("disabled Quarkus planning should not inspect artifacts");
                },
                () -> {
                    throw new AssertionError("disabled Quarkus planning should not request platform properties");
                });

        assertEquals(List.of(), planner.plan(request));
    }

    @Test
    void plansDeploymentAndParentFirstRequestsInDeterministicOrder() throws IOException {
        writeExtensionJar(
                jarPath("quarkus-zeta", "3.33.2"),
                """
                deployment-artifact=io.quarkus:quarkus-zeta-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-core::jar
                runner-parent-first-artifacts=org.jboss.logmanager:jboss-logmanager::jar,com.example:native-helper::zip
                """);
        writeExtensionJar(
                jarPath("quarkus-alpha", "3.33.2"),
                """
                deployment-artifact=io.quarkus:quarkus-alpha-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-core::jar
                """);
        writeExtensionJar(jarPath("plain-runtime", "1.0.0"), "");
        Files.writeString(jarPath("corrupt-runtime", "1.0.0"), "not a jar");

        DependencyRequest platformProperties = new DependencyRequest(
                new PackageId("io.quarkus.platform", "quarkus-platform-descriptor"),
                "3.33.2",
                DependencyScope.QUARKUS_DEPLOYMENT,
                RequestOrigin.TRANSITIVE,
                Optional.of(new ArtifactDescriptor(
                        new Coordinate(
                                "io.quarkus.platform",
                                "quarkus-platform-descriptor",
                                Optional.of("3.33.2")),
                        Optional.of("quarkus-platform-descriptor"),
                        "properties")));

        List<DependencyRequest> requests = planner.plan(new FrameworkDependencyRequestPlanRequest(
                config(true),
                List.of(
                        candidate("io.quarkus", "quarkus-zeta", "3.33.2", DependencyScope.COMPILE),
                        candidate("com.example", "plain-runtime", "1.0.0", DependencyScope.RUNTIME),
                        candidate("io.quarkus", "quarkus-alpha", "3.33.2", DependencyScope.COMPILE),
                        candidate("com.example", "corrupt-runtime", "1.0.0", DependencyScope.RUNTIME),
                        candidate("io.quarkus", "quarkus-test-only", "3.33.2", DependencyScope.TEST)),
                Map.of(new PackageId("io.quarkus", "quarkus-core"), "3.33.2"),
                Map.of(new PackageId("org.jboss.logmanager", "jboss-logmanager"), "3.1.2"),
                coordinate -> jarPath(coordinate.artifactId(), coordinate.version().orElseThrow()),
                () -> List.of(platformProperties)));

        assertEquals(
                List.of(
                        "io.quarkus:quarkus-alpha-deployment:3.33.2:QUARKUS_DEPLOYMENT::jar",
                        "io.quarkus:quarkus-core:3.33.2:QUARKUS_DEPLOYMENT::jar",
                        "io.quarkus:quarkus-zeta-deployment:3.33.2:QUARKUS_DEPLOYMENT::jar",
                        "org.jboss.logmanager:jboss-logmanager:3.1.2:QUARKUS_DEPLOYMENT::jar",
                        "io.quarkus.platform:quarkus-platform-descriptor:3.33.2:QUARKUS_DEPLOYMENT:"
                                + "quarkus-platform-descriptor:properties"),
                requests.stream().map(QuarkusDependencyRequestPlannerTest::requestKey).toList());
        assertTrue(requests.stream().allMatch(request -> request.origin() == RequestOrigin.TRANSITIVE));
    }

    @Test
    void unsupportedDeploymentArtifactTypeFailsWithActionableDiagnostic() throws IOException {
        writeExtensionJar(
                jarPath("quarkus-custom", "1.0.0"),
                "deployment-artifact=com.example:quarkus-custom-deployment::zip:1.0.0\n");

        ResolveException exception = assertThrows(
                ResolveException.class,
                () -> planner.plan(new FrameworkDependencyRequestPlanRequest(
                        config(true),
                        List.of(candidate("com.example", "quarkus-custom", "1.0.0", DependencyScope.COMPILE)),
                        Map.of(),
                        Map.of(),
                        coordinate -> jarPath(coordinate.artifactId(), coordinate.version().orElseThrow()),
                        List::of)));

        assertTrue(exception.getMessage().contains("declares deployment artifact"));
        assertTrue(exception.getMessage().contains("supports only jar deployment artifacts"));
        assertTrue(exception.getMessage().contains("Remove that extension"));
    }

    private Path jarPath(String artifactId, String version) {
        return tempDir.resolve(artifactId + "-" + version + ".jar");
    }

    private static FrameworkDependencyCandidate candidate(
            String groupId,
            String artifactId,
            String version,
            DependencyScope scope) {
        return new FrameworkDependencyCandidate(
                new PackageId(groupId, artifactId),
                version,
                List.of(scope));
    }

    private static ProjectConfig config(boolean quarkusEnabled) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        BuildSettings.defaults())
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        quarkusEnabled,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private static String requestKey(DependencyRequest request) {
        return request.packageId()
                + ":"
                + request.requestedVersion()
                + ":"
                + request.scope()
                + ":"
                + request.artifactDescriptor()
                        .flatMap(ArtifactDescriptor::classifier)
                        .orElse("")
                + ":"
                + request.artifactDescriptor()
                        .map(ArtifactDescriptor::extension)
                        .orElse("jar");
    }

    private static void writeExtensionJar(Path jarPath, String metadata) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            if (!metadata.isEmpty()) {
                output.putNextEntry(new JarEntry(METADATA_PATH));
                output.write(metadata.getBytes(StandardCharsets.UTF_8));
                output.closeEntry();
            } else {
                output.putNextEntry(new JarEntry("com/example/App.class"));
                output.write(0);
                output.closeEntry();
            }
        }
    }
}
