package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.QuarkusPackageMode;
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
        assertEquals("io.quarkus.bootstrap.app.QuarkusBootstrap", descriptor.bootstrapClass());
        assertEquals("io.quarkus.bootstrap.app.AugmentAction", descriptor.augmentActionClass());
        assertEquals(request.runtimeClasspath(), descriptor.runtimeClasspath());
        assertEquals(request.deploymentClasspath(), descriptor.deploymentClasspath());
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
                inputFingerprint=%s
                """.formatted(
                projectDir,
                projectDir.resolve("target/classes"),
                projectDir.resolve("target/quarkus"),
                projectDir.resolve("target/quarkus-app"),
                projectDir.resolve("target/quarkus/runtime-classpath.txt"),
                projectDir.resolve("target/quarkus/deployment-classpath.txt"),
                request.inputFingerprint()), Files.readString(descriptor.descriptorFile()));
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
                "sha256:" + "1".repeat(64),
                projectDir.resolve("target/quarkus/zolt-augmentation.properties"),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest.jar")),
                List.of(projectDir.resolve(".zolt/cache/io/quarkus/quarkus-rest-deployment.jar")),
                List.of());
    }
}
