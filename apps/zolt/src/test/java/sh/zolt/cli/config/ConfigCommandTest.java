package sh.zolt.cli.config;

import static sh.zolt.cli.CliTestSupport.execute;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.cli.CliTestSupport.CommandResult;
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
        assertTrue(result.stdout().contains("config file: missing"));
        assertTrue(result.stdout().contains("schema version: 1 (source: built-in default)"));
        assertTrue(result.stdout().contains("machine preferences only: yes"));
        assertTrue(result.stdout().contains("project semantics source: committed zolt.toml"));
        assertTrue(result.stdout().contains("repository.downloadConcurrency: 8 (source: built-in default)"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal.kind: maven-local (source: built-in default)"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal.enabled: false (source: built-in default)"));
        assertTrue(result.stdout().contains("defaults.toolchain.java: none (source: built-in default)"));
        assertTrue(result.stdout().contains("local overlay CI policy: reject with --no-local-overlays or zolt check --context ci"));
        assertTrue(result.stdout().contains("repository credentials stay in env references from committed project config"));
    }

    @Test
    void configShowQuietSuppressesHumanDiagnostics() {
        Path configPath = tempDir.resolve("missing/config.toml");

        CommandResult result = execute("--quiet", "config", "show", "--config", configPath.toString());

        assertEquals(0, result.exitCode());
        assertEquals("", result.stdout());
        assertEquals("", result.stderr());
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

                [repositoryOverlays.mavenLocal]
                enabled = true

                [defaults.toolchain.java]
                version = "21"
                distribution = "temurin"
                features = []

                [ui]
                color = "never"
                """);

        CommandResult result = execute("config", "show", "--config", configPath.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains("config file: present"));
        assertTrue(result.stdout().contains("schema version: 1 (source: user global config)"));
        assertTrue(result.stdout().contains("cache.root: " + tempDir.resolve("cache").toAbsolutePath().normalize() + " (source: user global config)"));
        assertTrue(result.stdout().contains("repository.downloadConcurrency: 3 (source: user global config)"));
        assertTrue(result.stdout().contains("repository.executionLane: platform (source: built-in default)"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal.kind: maven-local (source: built-in default)"));
        assertTrue(result.stdout().contains("repositoryOverlays.mavenLocal.enabled: true (source: user global config)"));
        assertTrue(result.stdout().contains(
                "defaults.toolchain.java: temurin 21 (features: none, policy: prefer-managed) (source: user global config)"));
        assertTrue(result.stdout().contains("ui.color: never (source: user global config)"));
        assertTrue(result.stdout().contains("ui.progress: auto (source: built-in default)"));
    }

    @Test
    void configShowPrintsNetworkSettings() throws IOException {
        Path configPath = tempDir.resolve("config.toml");
        Files.writeString(configPath, """
                version = 1

                [network]
                caBundle = "corp-ca.pem"
                toolchainMirror = "https://nexus.example.com/github"
                """);

        CommandResult result = execute("config", "show", "--config", configPath.toString());

        assertEquals(0, result.exitCode());
        assertTrue(result.stdout().contains(
                "network.caBundle: "
                        + tempDir.resolve("corp-ca.pem").toAbsolutePath().normalize()
                        + " (source: user global config)"));
        assertTrue(result.stdout().contains(
                "network.toolchainMirror: https://nexus.example.com/github (source: user global config)"));
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
