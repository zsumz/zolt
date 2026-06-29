package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class OpenApiGeneratedSourcePathsTest {
    @TempDir
    private Path projectDir;

    @Test
    void resolvesProjectRelativePathUnderRoot() {
        Path result = OpenApiGeneratedSourcePaths.inputPath(
                root(),
                "src/main/openapi/public-api.yaml",
                "main",
                "public-api",
                "input");

        assertEquals(root().resolve("src/main/openapi/public-api.yaml"), result);
    }

    @Test
    void rejectsAbsolutePath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.inputPath(
                        root(),
                        root().resolve("src/main/openapi/public-api.yaml").toString(),
                        "main",
                        "public-api",
                        "input"));

        assertInvalidPathMessage(exception, "input", root().resolve("src/main/openapi/public-api.yaml").toString());
    }

    @Test
    void rejectsEscapingPath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.inputPath(
                        root(),
                        "../outside.yaml",
                        "main",
                        "public-api",
                        "input"));

        assertInvalidPathMessage(exception, "input", "../outside.yaml");
    }

    @Test
    void rejectsProjectRootPath() {
        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.outputPath(
                        root(),
                        ".",
                        "main",
                        "public-api",
                        "output"));

        assertInvalidPathMessage(exception, "output", ".");
    }

    @Test
    void rejectsInputSymlinkEscape() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-input-", ".yaml");
        createSymlink(projectDir.resolve("src/main/openapi/public-api.yaml"), outside);

        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.inputPath(
                        root(),
                        "src/main/openapi/public-api.yaml",
                        "main",
                        "public-api",
                        "input"));

        assertInvalidPathMessage(exception, "input", "src/main/openapi/public-api.yaml");
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void rejectsConfigSymlinkEscape() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-config-", ".yaml");
        createSymlink(projectDir.resolve("src/main/openapi/config.yaml"), outside);

        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.inputPath(
                        root(),
                        "src/main/openapi/config.yaml",
                        "main",
                        "public-api",
                        "config"));

        assertInvalidPathMessage(exception, "config", "src/main/openapi/config.yaml");
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void rejectsTemplateDirectorySymlinkEscape() throws IOException {
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-template-");
        createSymlink(projectDir.resolve("src/main/openapi/templates"), outside);

        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.inputPath(
                        root(),
                        "src/main/openapi/templates",
                        "main",
                        "public-api",
                        "templateDir"));

        assertInvalidPathMessage(exception, "templateDir", "src/main/openapi/templates");
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    @Test
    void rejectsOutputSymlinkedParentEscape() throws IOException {
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-output-");
        createSymlink(projectDir.resolve("target"), outside);

        BuildException exception = assertThrows(
                BuildException.class,
                () -> OpenApiGeneratedSourcePaths.outputPath(
                        root(),
                        "target/generated/sources/openapi/public-api",
                        "main",
                        "public-api",
                        "output"));

        assertInvalidPathMessage(exception, "output", "target/generated/sources/openapi/public-api");
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
    }

    private Path root() {
        return projectDir.toAbsolutePath().normalize();
    }

    private static void assertInvalidPathMessage(BuildException exception, String field, String configuredPath) {
        assertTrue(exception.getMessage().contains("Invalid OpenAPI " + field + " path `" + configuredPath + "`"));
        assertTrue(exception.getMessage().contains("[generated.main.public-api]"));
        assertTrue(exception.getMessage().contains("[generated.main.public-api]." + field));
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        Files.createDirectories(link.getParent());
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }
}
