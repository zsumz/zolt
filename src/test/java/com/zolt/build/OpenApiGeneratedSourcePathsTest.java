package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

final class OpenApiGeneratedSourcePathsTest {
    private static final Path PROJECT_ROOT = Path.of("/workspace/demo").toAbsolutePath().normalize();

    @Test
    void resolvesProjectRelativePathUnderRoot() {
        Path result = OpenApiGeneratedSourcePaths.safeProjectPath(
                PROJECT_ROOT,
                "src/main/openapi/public-api.yaml",
                "main",
                "public-api",
                "input");

        assertEquals(PROJECT_ROOT.resolve("src/main/openapi/public-api.yaml"), result);
    }

    @Test
    void rejectsAbsolutePath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.safeProjectPath(
                        PROJECT_ROOT,
                        PROJECT_ROOT.resolve("src/main/openapi/public-api.yaml").toString(),
                        "main",
                        "public-api",
                        "input"));

        assertInvalidPathMessage(exception, "input");
    }

    @Test
    void rejectsEscapingPath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.safeProjectPath(
                        PROJECT_ROOT,
                        "../outside.yaml",
                        "main",
                        "public-api",
                        "input"));

        assertInvalidPathMessage(exception, "input");
    }

    @Test
    void rejectsProjectRootPath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.safeProjectPath(
                        PROJECT_ROOT,
                        ".",
                        "main",
                        "public-api",
                        "output"));

        assertInvalidPathMessage(exception, "output");
    }

    private static void assertInvalidPathMessage(BuildException exception, String field) {
        assertTrue(exception.getMessage().contains("Invalid OpenAPI " + field + " path"));
        assertTrue(exception.getMessage().contains("[generated.main.public-api]"));
        assertTrue(exception.getMessage().contains("Use a project-relative path under the project directory."));
    }
}
