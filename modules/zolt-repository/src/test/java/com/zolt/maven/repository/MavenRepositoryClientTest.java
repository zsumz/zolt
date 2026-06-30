package com.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MavenRepositoryClientTest extends MavenRepositoryClientTestSupport {
    @Test
    void fetchesKnownPom() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        put("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", "<project/>");

        RepositoryArtifact artifact = client.fetchPom(baseUri, coordinate);

        assertEquals(coordinate, artifact.coordinate());
        assertEquals("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom", artifact.path());
        assertEquals(
                baseUri.resolve("com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom"),
                artifact.source());
        assertArrayEquals("<project/>".getBytes(java.nio.charset.StandardCharsets.UTF_8), artifact.bytes());
    }

    @Test
    void fetchesKnownJar() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};
        responses.put("/maven2/com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar", jarBytes);

        RepositoryArtifact artifact = client.fetchJar(baseUri, coordinate);

        assertArrayEquals(jarBytes, artifact.bytes());
    }

    @Test
    void fetchesClassifierArtifact() {
        Coordinate coordinate = parser.parse("io.quarkus:quarkus-custom-deployment:1.0.0");
        byte[] jarBytes = new byte[] {0x50, 0x4b, 0x03, 0x04};
        responses.put(
                "/maven2/io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                jarBytes);

        RepositoryArtifact artifact = client.fetchArtifact(
                baseUri,
                ArtifactDescriptor.jar(coordinate, Optional.of("deployment")));

        assertEquals(
                "io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                artifact.path());
        assertArrayEquals(jarBytes, artifact.bytes());
    }
}
