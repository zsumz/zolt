package com.zolt.quarkus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class QuarkusExtensionMetadataReaderTest {
    @TempDir
    private Path tempDir;

    private final QuarkusExtensionMetadataReader reader = new QuarkusExtensionMetadataReader();

    @Test
    void returnsEmptyWhenJarDoesNotContainQuarkusMetadata() throws IOException {
        Path jar = tempDir.resolve("plain.jar");
        writeJar(jar, "com/example/App.class", "");

        assertTrue(reader.readIfPresent(jar).isEmpty());
    }

    @Test
    void parsesQuarkusExtensionMetadata() throws IOException {
        Path jar = tempDir.resolve("quarkus-rest.jar");
        writeJar(
                jar,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0
                parent-first-artifacts=org.jboss.logmanager:jboss-logmanager::jar
                runner-parent-first-artifacts=io.quarkus:quarkus-bootstrap-runner::jar
                excluded-artifacts=com.example:legacy-api
                lesser-priority-artifacts=com.example:shadowed-lib::jar
                removed-resources.com.example\\:legacy-api=old/Thing.class,META-INF/legacy.properties
                provides-capabilities=io.quarkus.rest,io.quarkus.resteasy.reactive
                requires-capabilities=io.quarkus.arc
                conditional-dependencies=io.quarkus:quarkus-smallrye-openapi
                """);

        QuarkusExtensionMetadata metadata = reader.readIfPresent(jar).orElseThrow();

        assertEquals("io.quarkus", metadata.deploymentArtifact().groupId());
        assertEquals("quarkus-rest-deployment", metadata.deploymentArtifact().artifactId());
        assertEquals("jar", metadata.deploymentArtifact().type());
        assertFalse(metadata.deploymentArtifact().classifier().isPresent());
        assertEquals("3.33.0", metadata.deploymentArtifact().version());
        assertEquals("io.quarkus:quarkus-rest-deployment:3.33.0", metadata.deploymentArtifact().toString());
        assertEquals(
                new QuarkusArtifactKey(
                        "org.jboss.logmanager",
                        "jboss-logmanager",
                        Optional.empty(),
                        Optional.of("jar")),
                metadata.parentFirstArtifacts().getFirst());
        assertEquals(
                "io.quarkus:quarkus-bootstrap-runner::jar",
                metadata.runnerParentFirstArtifacts().getFirst().toString());
        assertEquals("com.example:legacy-api", metadata.excludedArtifacts().getFirst().toString());
        assertEquals("com.example:shadowed-lib::jar", metadata.lesserPriorityArtifacts().getFirst().toString());
        assertEquals(
                List.of("old/Thing.class", "META-INF/legacy.properties"),
                metadata.removedResources().get(new QuarkusArtifactKey(
                        "com.example",
                        "legacy-api",
                        Optional.empty(),
                        Optional.empty())));
        assertEquals(List.of("io.quarkus.rest", "io.quarkus.resteasy.reactive"), metadata.providesCapabilities());
        assertEquals(List.of("io.quarkus.arc"), metadata.requiresCapabilities());
        assertEquals(List.of("io.quarkus:quarkus-smallrye-openapi"), metadata.conditionalDependencies());
    }

    @Test
    void parsesDeploymentArtifactWithClassifierAndType() throws IOException {
        Path jar = tempDir.resolve("classified.jar");
        writeJar(
                jar,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                "deployment-artifact=com.example:quarkus-feature-deployment:deployment:jar:1.0.0\n");

        QuarkusDeploymentArtifact artifact = reader.readIfPresent(jar).orElseThrow().deploymentArtifact();

        assertEquals("com.example:quarkus-feature-deployment:deployment:jar:1.0.0", artifact.toString());
        assertEquals(
                new QuarkusArtifactKey(
                        "com.example",
                        "quarkus-feature-deployment",
                        Optional.of("deployment"),
                        Optional.of("jar")),
                artifact.key());
    }

    @Test
    void malformedDeploymentArtifactFailsWithActionableMessage() throws IOException {
        Path jar = tempDir.resolve("broken.jar");
        writeJar(
                jar,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                "deployment-artifact=io.quarkus:quarkus-rest-deployment\n");

        QuarkusMetadataException exception = assertThrows(
                QuarkusMetadataException.class,
                () -> reader.readIfPresent(jar));

        assertTrue(exception.getMessage().contains("Invalid Quarkus extension metadata"));
        assertTrue(exception.getMessage().contains("Use `group:artifact:version`"));
        assertTrue(exception.getMessage().contains("Refresh the dependency cache"));
    }

    @Test
    void malformedArtifactKeyFailsWithActionableMessage() throws IOException {
        Path jar = tempDir.resolve("broken-key.jar");
        writeJar(
                jar,
                QuarkusExtensionMetadataReader.METADATA_PATH,
                """
                deployment-artifact=io.quarkus:quarkus-rest-deployment:3.33.0
                parent-first-artifacts=org.jboss.logmanager
                """);

        QuarkusMetadataException exception = assertThrows(
                QuarkusMetadataException.class,
                () -> reader.readIfPresent(jar));

        assertTrue(exception.getMessage().contains("Malformed Quarkus artifact key"));
        assertTrue(exception.getMessage().contains("parent-first-artifacts"));
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
}
