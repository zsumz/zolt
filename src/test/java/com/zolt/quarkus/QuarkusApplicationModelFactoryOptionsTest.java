package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusApplicationModelFactoryOptionsTest {
    @TempDir
    private Path tempDir;

    @Test
    void addsClassLoadingArtifactsFromRuntimeExtensionMetadata() throws IOException {
        Path quarkusCore = tempDir.resolve("quarkus-core.jar");
        writeJar(
                quarkusCore,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-core-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-bootstrap-runner::jar
                runner-parent-first-artifacts=io.quarkus:quarkus-bootstrap-runner::jar
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-bootstrap-runner"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        Path.of("/cache/quarkus-bootstrap-runner.jar"),
                        false)),
                List.of(quarkusCore),
                List.of()));

        QuarkusApplicationModelFactoryTest.FakeApplicationModel model =
                assertInstanceOf(QuarkusApplicationModelFactoryTest.FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                        "io.quarkus",
                        "quarkus-bootstrap-runner",
                        "",
                        "jar")),
                model.parentFirstArtifacts());
        assertEquals(
                List.of(new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                        "io.quarkus",
                        "quarkus-bootstrap-runner",
                        "",
                        "jar")),
                model.runnerParentFirstArtifacts());
    }

    @Test
    void addsClassLoadingArtifactsFromOptions() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.empty(),
                new QuarkusApplicationModelOptions(
                        List.of(new QuarkusArtifactKey(
                                "io.quarkus",
                                "quarkus-builder",
                                java.util.Optional.empty(),
                                java.util.Optional.empty())),
                        List.of(),
                        Map.of()));

        QuarkusApplicationModelFactoryTest.FakeApplicationModel model =
                assertInstanceOf(QuarkusApplicationModelFactoryTest.FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                        "io.quarkus",
                        "quarkus-builder",
                        "",
                        "jar")),
                model.parentFirstArtifacts());
        assertEquals(List.of(), model.runnerParentFirstArtifacts());
    }

    @Test
    void deduplicatesClassLoadingArtifactsFromOptionsAndRuntimeExtensionMetadata() throws IOException {
        Path quarkusCore = tempDir.resolve("quarkus-core.jar");
        writeJar(
                quarkusCore,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-core-deployment:3.33.2
                parent-first-artifacts=io.quarkus:quarkus-builder::jar
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(
                        List.of(),
                        List.of(new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-builder"),
                                "3.33.2",
                                DependencyScope.COMPILE,
                                Path.of("/cache/quarkus-builder.jar"),
                                false)),
                        List.of(quarkusCore),
                        List.of()),
                java.util.Optional.empty(),
                QuarkusApplicationModelOptions.TEST_BOOTSTRAP);

        QuarkusApplicationModelFactoryTest.FakeApplicationModel model =
                assertInstanceOf(QuarkusApplicationModelFactoryTest.FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                List.of(
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-builder",
                                "",
                                "jar"),
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "org.eclipse.microprofile.config",
                                "microprofile-config-api",
                                "",
                                "jar"),
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.smallrye.config",
                                "smallrye-config",
                                "",
                                "jar"),
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.smallrye.config",
                                "smallrye-config-common",
                                "",
                                "jar"),
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.smallrye.config",
                                "smallrye-config-core",
                                "",
                                "jar")),
                model.parentFirstArtifacts());
    }

    @Test
    void addsRemovedResourcesFromRuntimeExtensionMetadata() throws IOException {
        Path quarkusRest = tempDir.resolve("quarkus-rest.jar");
        writeJar(
                quarkusRest,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.2
                removed-resources.io.quarkus\\:quarkus-rest=\
                META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider,\
                META-INF/duplicate.index
                """);
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(descriptor(
                List.of(),
                List.of(new QuarkusBootstrapDependency(
                        new PackageId("io.quarkus", "quarkus-rest"),
                        "3.33.2",
                        DependencyScope.COMPILE,
                        quarkusRest,
                        true)),
                List.of(quarkusRest),
                List.of()));

        QuarkusApplicationModelFactoryTest.FakeApplicationModel model =
                assertInstanceOf(QuarkusApplicationModelFactoryTest.FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-rest",
                                "",
                                "jar"),
                        List.of(
                                "META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider",
                                "META-INF/duplicate.index")),
                model.removedResources());
    }

    @Test
    void addsTestBootstrapRemovedResourceOption() {
        QuarkusApplicationModelFactory factory = new QuarkusApplicationModelFactory(fakeApiWithArtifactKey());

        QuarkusApplicationModelHandle handle = factory.create(
                descriptor(),
                java.util.Optional.empty(),
                QuarkusApplicationModelOptions.TEST_BOOTSTRAP);

        QuarkusApplicationModelFactoryTest.FakeApplicationModel model =
                assertInstanceOf(QuarkusApplicationModelFactoryTest.FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-rest",
                                "",
                                "jar"),
                        List.of("META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider"),
                        new QuarkusApplicationModelFactoryTest.FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-arc",
                                "",
                                "jar"),
                        List.of("META-INF/services/io.quarkus.runtime.test.TestScopeSetup")),
                model.removedResources());
    }

    private static QuarkusBootstrapDescriptor descriptor() {
        return descriptor(
                List.of(),
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
                                false)),
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

    private static QuarkusApplicationModelApi fakeApiWithArtifactKey() {
        return new QuarkusApplicationModelApi(
                QuarkusApplicationModelFactoryTest.FakeApplicationModelBuilder.class.getName(),
                QuarkusApplicationModelFactoryTest.FakeResolvedDependencyBuilder.class.getName(),
                QuarkusApplicationModelFactoryTest.FakePlatformImports.class.getName(),
                QuarkusApplicationModelFactoryTest.FakePlatformImportsImpl.class.getName(),
                QuarkusApplicationModelFactoryTest.FakeArtifactKey.class.getName());
    }

    private static void writeJar(Path jarPath, String entryName, String content) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
