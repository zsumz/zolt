package com.zolt.maven;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
