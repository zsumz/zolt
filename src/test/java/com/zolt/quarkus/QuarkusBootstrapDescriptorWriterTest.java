package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.QuarkusPackageMode;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.DependencyScope;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusBootstrapDescriptorWriterTest {
    @TempDir
    private Path projectDir;

    private final QuarkusBootstrapDescriptorWriter writer = new QuarkusBootstrapDescriptorWriter();

    @Test
    void writesDeterministicBootstrapDescriptorAndClasspathFiles() throws IOException {
        QuarkusAugmentationRequest request = request();

        QuarkusBootstrapDescriptor descriptor = writer.write(request);

        assertEquals(projectDir.resolve("target/quarkus/zolt-bootstrap.properties"), descriptor.descriptorFile());
        assertEquals(projectDir.resolve("target/quarkus/runtime-classpath.txt"), descriptor.runtimeClasspathFile());
        assertEquals(projectDir.resolve("target/quarkus/deployment-classpath.txt"), descriptor.deploymentClasspathFile());
        assertEquals(projectDir.resolve("target/quarkus/platform-properties.txt"), descriptor.platformPropertiesFile());
        assertEquals(projectDir.resolve("target/quarkus/application-model.properties"), descriptor.applicationModelFile());
        assertEquals("io.quarkus.bootstrap.app.QuarkusBootstrap", descriptor.bootstrapClass());
        assertEquals("io.quarkus.bootstrap.app.AugmentAction", descriptor.augmentActionClass());
        assertEquals(request.applicationArtifact(), descriptor.applicationArtifact());
        assertEquals(request.runtimeClasspath(), descriptor.runtimeClasspath());
        assertEquals(request.deploymentClasspath(), descriptor.deploymentClasspath());
        assertEquals(
                request.platformPropertiesArtifacts().stream()
                        .map(QuarkusPlatformPropertiesArtifact::path)
                        .toList(),
                descriptor.platformPropertiesFiles());
        assertEquals(request.bootstrapDependencies(), descriptor.bootstrapDependencies());
        assertEquals("""
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
                platformPropertiesFile=%s
                applicationModelFile=%s
                inputFingerprint=%s
                """.formatted(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/quarkus"),
                projectDir.resolve("target/quarkus-app"),
                projectDir.resolve("target/quarkus/runtime-classpath.txt"),
                projectDir.resolve("target/quarkus/deployment-classpath.txt"),
                projectDir.resolve("target/quarkus/platform-properties.txt"),
                projectDir.resolve("target/quarkus/application-model.properties"),
                request.inputFingerprint()), Files.readString(descriptor.descriptorFile()));
        assertEquals("""
                version=1
                application.groupId=com.example
                application.artifactId=demo
                application.version=1.0.0
                application.classifier=
                application.type=jar
                application.path=%s
                dependencyCount=2
                dependency.0.groupId=io.quarkus
                dependency.0.artifactId=quarkus-rest
                dependency.0.version=3.33.0
                dependency.0.classifier=
                dependency.0.type=jar
                dependency.0.scope=compile
                dependency.0.path=%s
                dependency.0.direct=true
                dependency.1.groupId=io.quarkus
                dependency.1.artifactId=quarkus-rest-deployment
                dependency.1.version=3.33.0
                dependency.1.classifier=
                dependency.1.type=jar
                dependency.1.scope=quarkus-deployment
                dependency.1.path=%s
                dependency.1.direct=false
                """.formatted(
                projectDir.resolve("target/classes"),
                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar"),
                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                Files.readString(descriptor.applicationModelFile()));
        assertEquals("""
                %s
                """.formatted(
                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                Files.readString(descriptor.runtimeClasspathFile()));
        assertEquals("""
                %s
                """.formatted(
                projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                Files.readString(descriptor.deploymentClasspathFile()));
        assertEquals("""
                %s
                """.formatted(
                projectDir.resolve(".zolt/cache/io/quarkus/platform/quarkus-bom-quarkus-platform-properties.properties")),
                Files.readString(descriptor.platformPropertiesFile()));
    }

    @Test
    void rejectsMissingRequest() {
        QuarkusAugmentationException exception = assertThrows(
                QuarkusAugmentationException.class,
                () -> writer.write(null));

        assertTrue(exception.getMessage().contains("request is required"));
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
