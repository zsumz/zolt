package com.zolt.quarkus;

import static com.zolt.quarkus.QuarkusApplicationModelFactoryTestSupport.fakeApi;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.quarkus.QuarkusApplicationModelFactoryArtifactDoubles.FakeArtifactKey;
import com.zolt.quarkus.QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImports;
import com.zolt.quarkus.QuarkusApplicationModelFactoryArtifactDoubles.FakePlatformImportsImpl;
import com.zolt.quarkus.QuarkusApplicationModelFactoryModelDoubles.FakeApplicationModel;
import com.zolt.quarkus.QuarkusApplicationModelFactoryModelDoubles.FakeResolvedDependencyBuilder;
import com.zolt.quarkus.QuarkusApplicationModelFactoryModelDoubles.IncompatibleApplicationModelBuilder;
import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusApplicationModelFactoryTest {
    @TempDir
    private Path tempDir;

    @Test
    void buildsApplicationModelFromDescriptorDependencies() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(descriptor());

        assertEquals(FakeApplicationModel.class.getName(), handle.applicationModelClass());
        assertEquals(2, handle.dependencyCount());
        assertEquals(1, handle.runtimeDependencyCount());
        assertEquals(1, handle.deploymentDependencyCount());
        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals("com.example", model.appArtifact().groupId());
        assertEquals("demo", model.appArtifact().artifactId());
        assertEquals("1.0.0", model.appArtifact().version());
        assertEquals("compile", model.appArtifact().scope());
        assertEquals(Path.of("/repo/target/classes"), model.appArtifact().path());
        assertTrue(model.appArtifact().runtimeClasspath());
        assertFalse(model.appArtifact().deploymentClasspath());

        FakeResolvedDependencyBuilder runtime = model.dependencies().get(0);
        assertEquals("io.quarkus", runtime.groupId());
        assertEquals("quarkus-rest", runtime.artifactId());
        assertEquals("compile", runtime.scope());
        assertTrue(runtime.runtimeClasspath());
        assertFalse(runtime.deploymentClasspath());
        assertTrue(runtime.direct());

        FakeResolvedDependencyBuilder deployment = model.dependencies().get(1);
        assertEquals("io.quarkus", deployment.groupId());
        assertEquals("quarkus-rest-deployment", deployment.artifactId());
        assertEquals("compile", deployment.scope());
        assertFalse(deployment.runtimeClasspath());
        assertTrue(deployment.deploymentClasspath());
        assertFalse(deployment.direct());
    }

    @Test
    void addsPlatformPropertiesToApplicationModel() throws IOException {
        Path platformProperties = tempDir.resolve("quarkus-platform.properties");
        Files.writeString(platformProperties, """
                platform.quarkus.native.builder-image=quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21
                zolt.example=true
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(List.of(platformProperties)));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        FakePlatformImportsImpl platformImports =
                assertInstanceOf(FakePlatformImportsImpl.class, model.platformImports());
        assertEquals(
                "quay.io/quarkus/ubi-quarkus-mandrel-builder-image:jdk-21",
                platformImports.properties().get("platform.quarkus.native.builder-image"));
        assertEquals("true", platformImports.properties().get("zolt.example"));
    }

    @Test
    void mergesRuntimeAndDeploymentEntriesForSameArtifactKey() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.smallrye.common", "smallrye-common-os"),
                                "2.13.4",
                                DependencyScope.COMPILE,
                                Path.of("/cache/smallrye-common-os.jar"),
                                false),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.smallrye.common", "smallrye-common-os"),
                                "2.13.4",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                Path.of("/cache/smallrye-common-os.jar"),
                                false))));

        assertEquals(1, handle.dependencyCount());
        assertEquals(1, handle.runtimeDependencyCount());
        assertEquals(1, handle.deploymentDependencyCount());
        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        FakeResolvedDependencyBuilder dependency = model.dependencies().get(0);
        assertEquals("io.smallrye.common", dependency.groupId());
        assertEquals("smallrye-common-os", dependency.artifactId());
        assertTrue(dependency.runtimeClasspath());
        assertTrue(dependency.deploymentClasspath());
    }

    @Test
    void addsWorkspaceModuleOutputsAsAdditionalTestClasspathElements() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApi());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.of(new QuarkusWorkspaceModuleInputs(
                        Path.of("/repo"),
                        Path.of("/repo/target"),
                        Path.of("/repo/src/main/java"),
                        Path.of("/repo/src/main/resources"),
                        Path.of("/repo/target/classes"),
                        Path.of("/repo/src/test/java"),
                        Path.of("/repo/src/test/resources"),
                        Path.of("/repo/target/test-classes"))));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        io.quarkus.bootstrap.workspace.FakeWorkspaceModule module =
                assertInstanceOf(io.quarkus.bootstrap.workspace.FakeWorkspaceModule.class,
                        model.appArtifact().workspaceModule());
        assertEquals(Path.of("/repo/zolt.toml"), module.buildFile());
        assertEquals(
                List.of("/repo/target/classes", "/repo/target/test-classes"),
                module.additionalTestClasspathElements());
        assertEquals(
                List.of("", "tests"),
                module.artifactSources().stream()
                        .map(io.quarkus.bootstrap.workspace.FakeArtifactSources::classifier)
                        .toList());
        assertTrue(model.appArtifact().workspaceModuleFlag());
        assertTrue(model.appArtifact().reloadable());
        assertEquals(
                List.of(new FakeArtifactKey("com.example", "demo", "", "jar")),
                model.reloadableWorkspaceModules());
    }

    @Test
    void rejectsMissingApplicationModelClasses() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                "missing.ApplicationModelBuilder",
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(descriptor()));

        assertTrue(exception.getMessage().contains("application model classes are missing"));
        assertTrue(exception.getMessage().contains("quarkus-bootstrap-app-model"));
    }

    @Test
    void rejectsIncompatibleApplicationModelApi() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(new QuarkusApplicationModelApi(
                IncompatibleApplicationModelBuilder.class.getName(),
                FakeResolvedDependencyBuilder.class.getName(),
                FakePlatformImports.class.getName(),
                FakePlatformImportsImpl.class.getName()));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> factory.create(descriptor()));

        assertTrue(exception.getMessage().contains("application model API is incompatible"));
        assertTrue(exception.getMessage().contains("setAppArtifact"));
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return descriptor(List.of());
    }

    private static QuarkusBootstrapDescriptor descriptor(List<Path> platformPropertiesFiles) {
        return descriptor(
                platformPropertiesFiles,
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                "3.33.0",
                                DependencyScope.COMPILE,
                                Path.of("/cache/quarkus-rest.jar"),
                                true),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest-deployment"),
                                "3.33.0",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                Path.of("/cache/quarkus-rest-deployment.jar"),
                                false)));
    }

    private static QuarkusBootstrapDescriptor descriptor(
            List<Path> platformPropertiesFiles,
            List<QuarkusBootstrapDependency> bootstrapDependencies) {
        return descriptor(
                platformPropertiesFiles,
                bootstrapDependencies,
                List.of(Path.of("/cache/quarkus-rest.jar")),
                List.of(Path.of("/cache/quarkus-rest-deployment.jar")));
    }

    private static QuarkusBootstrapDescriptor descriptor(
            List<Path> platformPropertiesFiles,
            List<QuarkusBootstrapDependency> bootstrapDependencies,
            List<Path> runtimeClasspath,
            List<Path> deploymentClasspath) {
        return new QuarkusBootstrapDescriptor(
                Path.of("/repo/target/quarkus/zolt-bootstrap.properties"),
                Path.of("/repo/target/quarkus/runtime-classpath.txt"),
                Path.of("/repo/target/quarkus/deployment-classpath.txt"),
                Path.of("/repo/target/quarkus/platform-properties.txt"),
                Path.of("/repo/target/quarkus/application-model.properties"),
                QuarkusBootstrapDescriptorWriter.BOOTSTRAP_CLASS,
                QuarkusBootstrapDescriptorWriter.AUGMENT_ACTION_CLASS,
                Path.of("/repo"),
                Path.of("/repo/target/classes"),
                Path.of("/repo/target/quarkus"),
                Path.of("/repo/target/quarkus-app"),
                "fast-jar",
                "sha256:" + "1".repeat(64),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        Path.of("/repo/target/classes")),
                runtimeClasspath,
                deploymentClasspath,
                platformPropertiesFiles,
                bootstrapDependencies);
    }

}
