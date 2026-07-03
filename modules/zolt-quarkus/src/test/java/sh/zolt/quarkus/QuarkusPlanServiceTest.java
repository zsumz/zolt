package sh.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.toml.LockfileReadException;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.FrameworkSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.project.QuarkusSettings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusPlanServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void plansExtensionBootstrapAndPlatformInputsFromLockfile() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        String restJar = "io/quarkus/quarkus-rest/3.33.2/quarkus-rest-3.33.2.jar";
        String restDeploymentJar =
                "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar";
        String utilityJar = "com/example/utility/1.0.0/utility-1.0.0.jar";
        String missingRuntimeJar = "com/example/missing-runtime/1.0.0/missing-runtime-1.0.0.jar";
        String platformProperties =
                "io/quarkus/platform/quarkus-bom-quarkus-platform-properties/3.33.2/"
                        + "quarkus-bom-quarkus-platform-properties-3.33.2.properties";

        writeJar(
                cacheRoot.resolve(restJar),
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.2\n");
        writeJar(cacheRoot.resolve(restDeploymentJar), "META-INF/MANIFEST.MF", "");
        writeJar(cacheRoot.resolve(utilityJar), "META-INF/MANIFEST.MF", "");
        Files.createDirectories(cacheRoot.resolve(platformProperties).getParent());
        Files.writeString(cacheRoot.resolve(platformProperties), "quarkus.platform.version=3.33.2\n");

        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(
                        jarPackage(
                                "io.quarkus",
                                "quarkus-rest-deployment",
                                "3.33.2",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                false,
                                restDeploymentJar),
                        artifactPackage(
                                "io.quarkus.platform",
                                "quarkus-bom-quarkus-platform-properties",
                                "3.33.2",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                platformProperties,
                                "properties"),
                        jarPackage(
                                "io.quarkus",
                                "quarkus-rest",
                                "3.33.2",
                                DependencyScope.COMPILE,
                                true,
                                restJar),
                        jarPackage(
                                "com.example",
                                "utility",
                                "1.0.0",
                                DependencyScope.COMPILE,
                                true,
                                utilityJar),
                        jarPackage(
                                "com.example",
                                "missing-runtime",
                                "1.0.0",
                                DependencyScope.RUNTIME,
                                false,
                                missingRuntimeJar)),
                List.of());

        QuarkusPlan plan = new QuarkusPlanService().plan(projectDir, config(), lockfile, cacheRoot);

        assertTrue(plan.hasDeploymentInputs());
        assertTrue(plan.allExtensionDeploymentsResolved());
        assertEquals(List.of(
                cacheRoot.resolve(missingRuntimeJar),
                cacheRoot.resolve(utilityJar),
                cacheRoot.resolve(restJar)), plan.runtimeClasspath());
        assertEquals(List.of(cacheRoot.resolve(restDeploymentJar)), plan.deploymentClasspath());
        assertEquals(
                List.of(cacheRoot.resolve(platformProperties)),
                plan.platformPropertiesArtifacts().stream()
                        .map(artifact -> artifact.path())
                        .toList());
        assertEquals(
                List.of(
                        "com.example:utility:compile",
                        "io.quarkus:quarkus-rest:compile",
                        "io.quarkus:quarkus-rest-deployment:quarkus-deployment",
                        "com.example:missing-runtime:runtime"),
                plan.bootstrapDependencies().stream()
                        .map(dependency -> dependency.packageId()
                                + ":"
                                + dependency.scope().lockfileName())
                        .toList());
        assertEquals(1, plan.extensions().size());
        QuarkusPlanExtension extension = plan.extensions().getFirst();
        assertEquals(new PackageId("io.quarkus", "quarkus-rest"), extension.runtimePackage());
        assertEquals(cacheRoot.resolve(restJar), extension.runtimeArtifact());
        assertEquals(
                QuarkusDeploymentArtifact.parse("io.quarkus:quarkus-rest-deployment:3.33.2"),
                extension.deploymentArtifact());
        assertEquals(Optional.of(cacheRoot.resolve(restDeploymentJar)), extension.deploymentArtifactPath());
        assertTrue(plan.inputFingerprint().startsWith("sha256:"));
    }

    @Test
    void rejectsDisabledQuarkusBeforeReadingLockfile() {
        ProjectConfig disabled = config().withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                false,
                QuarkusPackageMode.FAST_JAR)));

        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(
                        projectDir,
                        disabled,
                        emptyLockfile(),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Quarkus is not enabled"));
        assertTrue(exception.getMessage().contains("Add `[framework.quarkus] enabled = true`"));
    }

    @Test
    void wrapsMalformedExtensionMetadataAsPlanningError() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        String badExtensionJar = "io/quarkus/quarkus-broken/1.0.0/quarkus-broken-1.0.0.jar";
        writeJar(
                cacheRoot.resolve(badExtensionJar),
                "META-INF/quarkus-extension.properties",
                "deployment-artifact=io.quarkus:quarkus-broken-deployment\n");
        ZoltLockfile lockfile = new ZoltLockfile(
                ZoltLockfile.CURRENT_VERSION,
                List.of(jarPackage(
                        "io.quarkus",
                        "quarkus-broken",
                        "1.0.0",
                        DependencyScope.COMPILE,
                        true,
                        badExtensionJar)),
                List.of());

        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(projectDir, config(), lockfile, cacheRoot));

        assertTrue(exception.getMessage().contains("Invalid Quarkus extension metadata"));
        assertTrue(exception.getMessage().contains("Refresh the dependency cache"));
    }

    @Test
    void failsBeforePlanningWorkerInputsWhenDeploymentJarDoesNotMatchLockfileHash() throws IOException {
        Path cacheRoot = projectDir.resolve("cache");
        Path deploymentJar = cacheRoot.resolve(
                "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar");
        Files.createDirectories(deploymentJar.getParent());
        Files.writeString(deploymentJar, "corrupted deployment jar bytes");
        Files.writeString(projectDir.resolve("zolt.lock"), """
                version = 1

                [[package]]
                id = "io.quarkus:quarkus-rest-deployment"
                version = "3.33.2"
                source = "maven-central"
                scope = "quarkus-deployment"
                direct = false
                jar = "io/quarkus/quarkus-rest-deployment/3.33.2/quarkus-rest-deployment-3.33.2.jar"
                jarSha256 = "0000000000000000000000000000000000000000000000000000000000000000"
                dependencies = []
                """);

        LockfileReadException exception = assertThrows(
                LockfileReadException.class,
                () -> new QuarkusPlanService().plan(projectDir, config(), cacheRoot));

        assertTrue(exception.getMessage().contains(
                "Cached jar integrity check failed for io.quarkus:quarkus-rest-deployment:3.33.2"));
    }

    @Test
    void rejectsUnsafeBuildOutputPath() {
        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(
                        projectDir,
                        config(new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                "../outside-classes",
                                "target/test-classes")),
                        emptyLockfile(),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("[build].output"));
        assertTrue(exception.getMessage().contains("../outside-classes"));
    }

    @Test
    void rejectsQuarkusOutputSymlinkThatEscapesProject() throws IOException {
        createSymlink(projectDir.resolve("target"), Files.createTempDirectory("zolt-quarkus-target-"));

        QuarkusPlanException exception = assertThrows(
                QuarkusPlanException.class,
                () -> new QuarkusPlanService().plan(
                        projectDir,
                        config(new BuildSettings(
                                "src/main/java",
                                "src/test/java",
                                "classes",
                                "test-classes")),
                        emptyLockfile(),
                        projectDir.resolve("cache")));

        assertTrue(exception.getMessage().contains("Quarkus augmentation output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    private static ProjectConfig config() {
        return config(BuildSettings.defaults());
    }

    private static ProjectConfig config(BuildSettings buildSettings) {
        return ProjectConfigs.withDirectDependencies(
                        new ProjectMetadata("demo", "1.0.0", "com.example", "21", Optional.empty()),
                        ProjectConfig.defaultRepositories(),
                        Map.of(),
                        Map.of(),
                        buildSettings)
                .withFrameworkSettings(new FrameworkSettings(new QuarkusSettings(
                        true,
                        QuarkusPackageMode.FAST_JAR)));
    }

    private static ZoltLockfile emptyLockfile() {
        return new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.of(), List.of());
    }

    private static LockPackage jarPackage(
            String groupId,
            String artifactId,
            String version,
            DependencyScope scope,
            boolean direct,
            String jar) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                scope,
                direct,
                Optional.of(jar),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                List.of());
    }

    private static LockPackage artifactPackage(
            String groupId,
            String artifactId,
            String version,
            DependencyScope scope,
            String artifact,
            String artifactType) {
        return new LockPackage(
                new PackageId(groupId, artifactId),
                version,
                "maven-central",
                scope,
                false,
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.empty(),
                Optional.of(artifact),
                Optional.of(artifactType),
                Optional.empty(),
                List.of());
    }

    private static void writeJar(Path jarPath, String entryName, String content) throws IOException {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            JarEntry entry = new JarEntry(entryName);
            output.putNextEntry(entry);
            output.write(content.getBytes());
            output.closeEntry();
        }
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
