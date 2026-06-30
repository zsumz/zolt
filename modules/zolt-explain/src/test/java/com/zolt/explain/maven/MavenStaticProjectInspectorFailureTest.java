package com.zolt.explain.maven;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.explain.MigrationExplainException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MavenStaticProjectInspectorFailureTest {
    @TempDir
    private Path tempDir;

    private final MavenStaticProjectInspector inspector = new MavenStaticProjectInspector();

    @Test
    void missingPomFailsWithActionableMessage() {
        MigrationExplainException exception = assertThrows(
                MigrationExplainException.class,
                () -> inspector.inspect(tempDir));

        assertTrue(exception.getMessage().contains("Expected pom.xml"));
        assertTrue(exception.getMessage().contains("pass --cwd"));
    }

    @Test
    void malformedPomFailsWithActionableMessage() throws IOException {
        Files.writeString(tempDir.resolve("pom.xml"), "<project><artifactId>broken</project>");

        MigrationExplainException exception = assertThrows(
                MigrationExplainException.class,
                () -> inspector.inspect(tempDir));

        assertTrue(exception.getMessage().contains("Fix malformed POM XML"));
        assertTrue(exception.getMessage().contains("pom.xml"));
    }
}
