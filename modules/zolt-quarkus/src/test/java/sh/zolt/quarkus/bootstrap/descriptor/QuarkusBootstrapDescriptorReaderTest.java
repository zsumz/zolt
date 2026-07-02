package sh.zolt.quarkus.bootstrap.descriptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.project.QuarkusPackageMode;
import sh.zolt.quarkus.QuarkusAugmentationException;
import sh.zolt.quarkus.production.QuarkusAugmentationRequest;
import sh.zolt.quarkus.production.QuarkusOutputLayout;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusBootstrapDescriptorReaderTest {
    @TempDir
    private Path projectDir;

    private final QuarkusBootstrapDescriptorWriter writer = new QuarkusBootstrapDescriptorWriter();
    private final QuarkusBootstrapDescriptorReader reader = new QuarkusBootstrapDescriptorReader();

    @Test
    void readsDescriptorWrittenByWriter() {
        QuarkusAugmentationRequest request = request();
        QuarkusBootstrapDescriptor written = writer.write(request);

        QuarkusBootstrapDescriptor read = reader.read(written.descriptorFile());

        assertEquals(written, read);
    }

    @Test
    void rejectsUnsupportedVersion() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, "version=2\n");

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Unsupported Quarkus bootstrap descriptor"));
        assertTrue(exception.getMessage().contains("Run Quarkus augmentation planning again"));
    }

    @Test
    void rejectsMissingRequiredField() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, """
                version=1
                bootstrapClass=io.quarkus.bootstrap.app.QuarkusBootstrap
                """);

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Missing required field `augmentActionClass`"));
    }

    @Test
    void rejectsMissingClasspathFile() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Files.writeString(descriptor, """
                version=1
                bootstrapClass=io.quarkus.bootstrap.app.QuarkusBootstrap
                augmentActionClass=io.quarkus.bootstrap.app.AugmentAction
                mode=prod
                package=fast-jar
                projectDirectory=%s
                applicationClasses=%s
                augmentationDirectory=%s
                packageDirectory=%s
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/quarkus"),
                projectDir.resolve("target/quarkus-app"),
                projectDir.resolve("target/quarkus/runtime-classpath.txt"),
                projectDir.resolve("target/quarkus/missing-deployment-classpath.txt"),
                projectDir.resolve("target/quarkus/application-model.properties"),
                "sha256:" + "1".repeat(64)));
        Files.writeString(projectDir.resolve("target/quarkus/runtime-classpath.txt"), "");
        Files.writeString(projectDir.resolve("target/quarkus/application-model.properties"), """
                version=1
                application.groupId=com.example
                application.artifactId=demo
                application.version=1.0.0
                application.classifier=
                application.type=jar
                application.path=%s
                dependencyCount=0
                """.formatted(projectDir.resolve("target/classes")));

        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> reader.read(descriptor));

        assertTrue(exception.getMessage().contains("Could not read Quarkus deployment classpath file"));
        assertTrue(exception.getMessage().contains("referenced by"));
    }

    @Test
    void preservesBackslashesInPathValues() throws IOException {
        Path descriptor = descriptorPath();
        Files.createDirectories(descriptor.getParent());
        Path runtimeClasspath = projectDir.resolve("target/quarkus/runtime-classpath.txt");
        Path deploymentClasspath = projectDir.resolve("target/quarkus/deployment-classpath.txt");
        Path applicationModel = projectDir.resolve("target/quarkus/application-model.properties");
        Files.writeString(runtimeClasspath, "");
        Files.writeString(deploymentClasspath, "");
        Files.writeString(applicationModel, """
                version=1
                application.groupId=com.example
                application.artifactId=demo
                application.version=1.0.0
                application.classifier=
                application.type=jar
                application.path=C:\\repo\\app\\target\\classes
                dependencyCount=0
                """);
        Files.writeString(descriptor, """
                version=1
                bootstrapClass=io.quarkus.bootstrap.app.QuarkusBootstrap
                augmentActionClass=io.quarkus.bootstrap.app.AugmentAction
                mode=prod
                package=fast-jar
                projectDirectory=C:\\repo\\app
                applicationClasses=C:\\repo\\app\\target\\classes
                augmentationDirectory=C:\\repo\\app\\target\\quarkus
                packageDirectory=C:\\repo\\app\\target\\quarkus-app
                runtimeClasspathFile=%s
                deploymentClasspathFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                runtimeClasspath,
                deploymentClasspath,
                applicationModel,
                "sha256:" + "1".repeat(64)));

        QuarkusBootstrapDescriptor read = reader.read(descriptor);

        assertEquals("C:\\repo\\app", read.projectDirectory().toString());
        assertEquals("C:\\repo\\app\\target\\classes", read.applicationClasses().toString());
    }

    private Path descriptorPath() {
        return projectDir.resolve("target/quarkus/zolt-bootstrap.properties");
    }

    private QuarkusAugmentationRequest request() {
        return new QuarkusAugmentationRequest(
                projectDir,
                projectDir.resolve("target/classes"),
                QuarkusPackageMode.FAST_JAR,
                new QuarkusOutputLayout(
                        projectDir.resolve("target/quarkus"),
                        projectDir.resolve("target/quarkus-app")),
                new QuarkusApplicationArtifact(
                        new PackageId("com.example", "demo"),
                        "1.0.0",
                        projectDir.resolve("target/classes")),
                "sha256:" + "1".repeat(64),
                projectDir.resolve("target/quarkus/zolt-augmentation.properties"),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of(new QuarkusPlatformPropertiesArtifact(
                        new PackageId("io.quarkus.platform", "quarkus-bom-quarkus-platform-properties"),
                        "3.33.0",
                        projectDir.resolve(".zolt/cache/io/quarkus/platform/quarkus-bom-quarkus-platform-properties.properties"))),
                List.of(
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest"),
                                "3.33.0",
                                DependencyScope.COMPILE,
                                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar"),
                                true),
                        new QuarkusBootstrapDependency(
                                new PackageId("io.quarkus", "quarkus-rest-deployment"),
                                "3.33.0",
                                DependencyScope.QUARKUS_DEPLOYMENT,
                                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar"),
                                false)),
                List.of());
    }
}
