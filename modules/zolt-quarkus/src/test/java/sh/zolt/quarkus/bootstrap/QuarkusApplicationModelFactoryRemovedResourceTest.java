package sh.zolt.quarkus.bootstrap;

import static sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryTestSupport.fakeApiWithArtifactKey;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryArtifactDoubles.FakeArtifactKey;
import sh.zolt.quarkus.bootstrap.QuarkusApplicationModelFactoryModelDoubles.FakeApplicationModel;
import sh.zolt.quarkus.bootstrap.descriptor.QuarkusBootstrapDependency;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusApplicationModelFactoryRemovedResourceTest {
    @TempDir
    private Path tempDir;

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

        QuarkusApplicationModelHandle handle = factory.create(
                QuarkusApplicationModelFactoryOptionsTest.descriptor(
                        List.of(),
                        List.of(new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                "3.33.2",
                                DependencyScope.COMPILE,
                                quarkusRest,
                                true)),
                        List.of(quarkusRest),
                        List.of()));

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new FakeArtifactKey(
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
                QuarkusApplicationModelFactoryOptionsTest.descriptor(),
                java.util.Optional.empty(),
                QuarkusApplicationModelOptions.TEST_BOOTSTRAP);

        FakeApplicationModel model = assertInstanceOf(FakeApplicationModel.class, handle.applicationModel());
        assertEquals(
                Map.of(
                        new FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-rest",
                                "",
                                "jar"),
                        List.of("META-INF/services/io.quarkus.runtime.test.TestHttpEndpointProvider"),
                        new FakeArtifactKey(
                                "io.quarkus",
                                "quarkus-arc",
                                "",
                                "jar"),
                        List.of("META-INF/services/io.quarkus.runtime.test.TestScopeSetup")),
                model.removedResources());
    }

    private static void writeJar(Path jarPath, String entryName, String content) throws IOException {
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jarPath))) {
            output.putNextEntry(new JarEntry(entryName));
            output.write(content.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            output.closeEntry();
        }
    }
}
