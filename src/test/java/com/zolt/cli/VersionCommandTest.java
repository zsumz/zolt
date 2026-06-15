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

final class VersionCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void versionPrintsZoltVersion() {
        CommandResult result = execute("--version");

        assertEquals(0, result.exitCode());
        assertEquals("0.1.0-SNAPSHOT\n", result.stdout());
    }

    @Test
    void versionCommandPrintsZoltVersion() {
        CommandResult result = execute("version");

        assertEquals(0, result.exitCode());
        assertEquals("0.1.0-SNAPSHOT\n", result.stdout());
    }

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

    @Test
    void versionRemoveDeletesUnusedAliasWithoutResolveWhenRequested() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-remove");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-remove"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                junit = "5.12.1"
                """);

        CommandResult result = execute(
                "version",
                "remove",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "guava");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Removed version alias guava from [versions]"));
        assertTrue(result.stdout().contains("Skipped resolve"));
        assertEquals("", result.stderr());
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertFalse(config.contains("\"guava\""));
        assertTrue(config.contains("[versions]\n\"junit\" = \"5.12.1\""));
        assertFalse(Files.exists(projectDir.resolve("zolt.lock")));
    }

    @Test
    void versionRemoveRefreshesLockfileByDefault() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-remove-resolve");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-remove-resolve"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"
                """);

        CommandResult result = execute(
                "version",
                "remove",
                "--cwd", projectDir.toString(),
                "guava");

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("Removed version alias guava from [versions]"));
        assertTrue(result.stdout().contains("Resolved 0 packages"));
        assertTrue(Files.exists(projectDir.resolve("zolt.lock")));
        assertFalse(Files.readString(projectDir.resolve("zolt.toml")).contains("[versions]"));
    }

    @Test
    void versionRemoveRejectsReferencedAlias() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-remove-referenced");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-remove-referenced"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                guava = "33.4.8-jre"

                [dependencies]
                "com.google.guava:guava" = { versionRef = "guava" }
                """);

        CommandResult result = execute(
                "version",
                "remove",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "guava");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Version alias `guava` is still referenced by [dependencies].com.google.guava:guava."));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("guava = \"33.4.8-jre\""));
        assertTrue(config.contains("\"com.google.guava:guava\" = { versionRef = \"guava\" }"));
    }

    @Test
    void versionRemoveRejectsAliasReferencedByPlatformConstraintAndOpenApiTool() throws IOException {
        Path projectDir = tempDir.resolve("version-alias-remove-reference-categories");
        Files.createDirectories(projectDir);
        Files.writeString(projectDir.resolve("zolt.toml"), """
                [project]
                name = "version-alias-remove-reference-categories"
                version = "0.1.0"
                group = "com.example"
                java = "21"

                [versions]
                shared = "1.0.0"

                [platforms]
                "com.example:platform" = { versionRef = "shared" }

                [dependencyConstraints]
                "com.example:core" = { versionRef = "shared", kind = "strict" }

                [generated.openapiTool]
                coordinate = "org.openapitools:openapi-generator-cli"
                versionRef = "shared"

                [generated.main.public-api]
                kind = "openapi"
                language = "java"
                input = "src/main/openapi/public-api.yaml"
                output = "target/generated/sources/openapi/public-api"
                generator = "spring"
                """);

        CommandResult result = execute(
                "version",
                "remove",
                "--cwd", projectDir.toString(),
                "--no-resolve",
                "shared");

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("[platforms].com.example:platform"));
        assertTrue(result.stderr().contains("[dependencyConstraints].com.example:core"));
        assertTrue(result.stderr().contains("[generated.openapiTool].versionRef"));
        String config = Files.readString(projectDir.resolve("zolt.toml"));
        assertTrue(config.contains("shared = \"1.0.0\""));
    }

}
