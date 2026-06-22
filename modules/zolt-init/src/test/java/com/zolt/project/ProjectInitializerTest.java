package com.zolt.project;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.toml.ZoltTomlParser;
import com.zolt.toml.ZoltTomlWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ProjectInitializerTest {
    private final ZoltTomlParser parser = new ZoltTomlParser();
    private final ProjectInitializer initializer = new ProjectInitializer(new ZoltTomlWriter()::write);

    @TempDir
    private Path tempDir;

    @Test
    void createsProjectFiles() {
        ProjectInitResult result = initializer.init(tempDir, "hello", "com.example", "21");

        assertTrue(Files.exists(result.configFile()));
        assertTrue(Files.exists(result.mainSource()));
        assertTrue(Files.exists(result.testSource()));
        assertTrue(Files.exists(result.projectDirectory().resolve(".gitignore")));
    }

    @Test
    void generatedConfigParsesCleanly() {
        ProjectInitResult result = initializer.init(tempDir, "hello", "com.example", "21");

        ProjectConfig config = parser.parse(result.configFile());

        assertEquals("hello", config.project().name());
        assertEquals("com.example", config.project().group());
        assertEquals("21", config.project().java());
        assertEquals("com.example.Main", config.project().main().orElseThrow());
    }

    @Test
    void generatedSourcesUseRequestedPackage() throws IOException {
        ProjectInitResult result = initializer.init(tempDir, "hello", "dev.zolt.demo", "21");

        assertEquals(
                result.projectDirectory().resolve("src/main/java/dev/zolt/demo/Main.java"),
                result.mainSource());
        assertTrue(Files.readString(result.mainSource()).contains("package dev.zolt.demo;"));
        assertTrue(Files.readString(result.testSource()).contains("final class MainTest"));
    }

    @Test
    void rejectsPathLikeProjectName() {
        ProjectInitException exception = assertThrows(
                ProjectInitException.class,
                () -> initializer.init(tempDir, "nested/hello", "com.example", "21"));

        assertEquals("Project name must be a directory name, not a path.", exception.getMessage());
    }

    @Test
    void rejectsInvalidGroup() {
        ProjectInitException exception = assertThrows(
                ProjectInitException.class,
                () -> initializer.init(tempDir, "hello", "com.123bad", "21"));

        assertEquals("Project group must be a valid Java package, for example `com.example`.", exception.getMessage());
    }

    @Test
    void refusesNonEmptyDirectory() throws IOException {
        Path existing = tempDir.resolve("hello");
        Files.createDirectories(existing);
        Files.writeString(existing.resolve("note.txt"), "already here");

        ProjectInitException exception = assertThrows(
                ProjectInitException.class,
                () -> initializer.init(tempDir, "hello", "com.example", "21"));

        assertTrue(exception.getMessage().contains("is not empty"));
    }
}
