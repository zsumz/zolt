package com.zolt.cli;

import static com.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.cli.CliTestSupport.CommandResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class VersionCommandSetTest {
    @TempDir
    private Path tempDir;

    @Test
    void versionSetAddsAndUpdatesAliasWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-set");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-set"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [repositories]
                "central" = "https://repo.maven.apache.org/maven2"
                """);

        CommandResult added = execute(
                "version",
                "set",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "guava",
                "33.4.8-jre");

        assertEquals(0, added.exitCode());
        assertTrue(added.stdout().contains("Added version alias guava = 33.4.8-jre to [versions]"));
        assertTrue(added.stdout().contains("Skipped resolve"));
        assertEquals("", added.stderr());
        String addedConfig = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(addedConfig.contains("[versions]\n\"guava\" = \"33.4.8-jre\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));

        CommandResult updated = execute(
                "version",
                "set",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "guava",
                "33.4.9-jre");

        assertEquals(0, updated.exitCode());
        assertTrue(updated.stdout().contains("Updated version alias guava from 33.4.8-jre to 33.4.9-jre in [versions]"));
        assertEquals("", updated.stderr());
        String updatedConfig = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(updatedConfig.contains("[versions]\n\"guava\" = \"33.4.9-jre\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void versionSetRejectsInvalidAliasNames() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-invalid");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-invalid"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        CommandResult result = execute(
                "version",
                "set",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "spring boot",
                "4.0.6");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Invalid version alias `spring boot`. Alias names may contain only letters, digits, dot, underscore, and hyphen."));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("[versions]"));
    }

    @Test
    void versionSetRejectsInvalidAliasValues() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-invalid-value");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-invalid-value"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        CommandResult result = execute(
                "version",
                "set",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "guava",
                "1.0-SNAPSHOT");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains(
                "Invalid version alias `1.0-SNAPSHOT` for [versions].guava. SNAPSHOT versions are not supported in this context"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("[versions]"));
    }

    @Test
    void versionSetRefreshesLockfileByDefault() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-resolve");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-resolve"
                version = "0.1.0"
                group = "com.example"
                java = "21"
                """);

        CommandResult result = execute(
                "version",
                "set",
                "--cwd", projectDir.toString(),
                "guava",
                "33.4.8-jre");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Added version alias guava = 33.4.8-jre to [versions]"));
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
    }
}
