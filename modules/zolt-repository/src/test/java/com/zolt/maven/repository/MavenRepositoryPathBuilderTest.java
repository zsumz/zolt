package com.zolt.maven.repository;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.zolt.maven.ArtifactDescriptor;
import com.zolt.maven.Coordinate;
import com.zolt.maven.CoordinateParseException;
import com.zolt.maven.CoordinateParser;
import java.util.Optional;
import org.junit.jupiter.api.Test;

final class MavenRepositoryPathBuilderTest {
    private final CoordinateParser parser = new CoordinateParser();
    private final MavenRepositoryPathBuilder paths = new MavenRepositoryPathBuilder();

    @Test
    void buildsPomPathForGuava() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        assertEquals(
                "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.pom",
                paths.pomPath(coordinate));
    }

    @Test
    void buildsJarPathForGuava() {
        Coordinate coordinate = parser.parse("com.google.guava:guava:33.4.0-jre");

        assertEquals(
                "com/google/guava/guava/33.4.0-jre/guava-33.4.0-jre.jar",
                paths.jarPath(coordinate));
    }

    @Test
    void buildsClassifierArtifactPath() {
        Coordinate coordinate = parser.parse("io.quarkus:quarkus-custom-deployment:1.0.0");

        assertEquals(
                "io/quarkus/quarkus-custom-deployment/1.0.0/quarkus-custom-deployment-1.0.0-deployment.jar",
                paths.artifactPath(ArtifactDescriptor.jar(coordinate, Optional.of("deployment"))));
    }

    @Test
    void convertsGroupToRepositoryPath() {
        Coordinate coordinate = parser.parse("org.junit.jupiter:junit-jupiter:5.11.4");

        assertEquals("org/junit/jupiter", paths.groupPath(coordinate));
    }

    @Test
    void requiresVersionForArtifactPath() {
        Coordinate coordinate = parser.parse("org.slf4j:slf4j-api");

        CoordinateParseException exception = assertThrows(
                CoordinateParseException.class,
                () -> paths.jarPath(coordinate));

        assertEquals(
                "Coordinate `org.slf4j:slf4j-api` needs a version to build a repository path.",
                exception.getMessage());
    }
}
