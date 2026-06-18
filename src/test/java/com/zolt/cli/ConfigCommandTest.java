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

final class ConfigCommandTest {
    @TempDir
    private Path tempDir;

    @Test
    void configShowPrintsBuiltInDefaultsWhenConfigIsMissing() {
        Path configPath = tempDir.resolve("missing/config.toml");

        CommandResult result = execute("config", "show", "--config", configPath.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("User global config: " + configPath.toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("config path source: flag"));
        assertTrue(result.stdout().contains("schema version: 1 (source: built-in default)"));
        assertTrue(result.stdout().contains("machine preferences only: yes"));
        assertTrue(result.stdout().contains("project semantics source: committed zolt.toml"));
        assertTrue(result.stdout().contains("repository.downloadConcurrency: 8 (source: built-in default)"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal: kind=maven-local, enabled=false"));
        assertTrue(result.stdout().contains("local overlay CI policy: reject with --no-local-overlays or zolt check --context ci"));
        assertTrue(result.stdout().contains("repository credentials stay in env references from committed project config"));
    }

    @Test
    void configShowPrintsConfiguredMachinePreferences() throws IOException {
        Path configPath = tempDir.resolve("config.toml");
        Files.writeString(configPath, """
                version = 1

                [cache]
                root = "cache"

                [repository]
                downloadConcurrency = 3
                executionLane = "serial"

                [repositoryOverlays.mavenLocal]
                kind = "maven-local"
                enabled = true

                [ui]
                color = "never"
                progress = "always"
                """);

        CommandResult result = execute("config", "show", "--config", configPath.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("schema version: 1 (source: user global config with built-in defaults)"));
        assertTrue(result.stdout().contains("cache.root: " + tempDir.resolve("cache").toAbsolutePath().normalize()));
        assertTrue(result.stdout().contains("repository.downloadConcurrency: 3"));
        assertTrue(result.stdout().contains("repository.executionLane: serial"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal: kind=maven-local, enabled=true"));
        assertTrue(result.stdout().contains("ui.color: never"));
        assertTrue(result.stdout().contains("ui.progress: always"));
    }

    @Test
    void configShowRejectsSemanticSectionsWithoutLeakingRepositoryUrl() throws IOException {
        Path configPath = tempDir.resolve("bad-config.toml");
        Files.writeString(configPath, """
                version = 1

                [repositories]
                private = "https://user:secret@example.test/maven"
                """);

        CommandResult result = execute("config", "show", "--config", configPath.toString());

        assertEquals(1, result.exitCode());
        assertTrue(result.stderr().contains("Global config section [repositories] is not supported"));
        assertTrue(result.stderr().contains("committed zolt.toml"));
        assertFalse(result.stderr().contains("secret"));
        assertFalse(result.stderr().contains("example.test"));
    }
}
