package com.zolt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UserGlobalConfigParserTest {
    @TempDir
    Path tempDir;

    @Test
    void missingConfigReturnsDefaults() {
        Path configPath = tempDir.resolve("missing/config.toml");

        UserGlobalConfig config = new UserGlobalConfigParser().read(configPath);

        assertEquals(1, config.version());
        assertEquals(configPath.toAbsolutePath().normalize(), config.configPath());
        assertTrue(config.cacheRoot().endsWith(Path.of(".zolt/cache")));
        assertEquals(8, config.repository().downloadConcurrency());
        assertEquals("platform", config.repository().executionLane());
        assertFalse(config.repositoryOverlays().get("mavenLocal").enabled());
        assertEquals("auto", config.ui().color());
        assertEquals("auto", config.ui().progress());
    }

    @Test
    void parsesAllowedSections() throws IOException {
        Path configPath = tempDir.resolve("zolt/config.toml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                version = 1

                [cache]
                root = "cache-dir"

                [repository]
                downloadConcurrency = 4
                executionLane = "serial"

                [repositoryOverlays.mavenLocal]
                kind = "maven-local"
                enabled = true

                [ui]
                color = "never"
                progress = "always"
                """);

        UserGlobalConfig config = new UserGlobalConfigParser().read(configPath);

        assertEquals(configPath.getParent().resolve("cache-dir").toAbsolutePath().normalize(), config.cacheRoot());
        assertEquals(4, config.repository().downloadConcurrency());
        assertEquals("serial", config.repository().executionLane());
        assertTrue(config.repositoryOverlays().get("mavenLocal").enabled());
        assertEquals("maven-local", config.repositoryOverlays().get("mavenLocal").kind());
        assertEquals("never", config.ui().color());
        assertEquals("always", config.ui().progress());
    }

    @Test
    void rejectsSemanticSections() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [repositories]
                        central = "https://repo.maven.apache.org/maven2"
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Global config section [repositories] is not supported"));
        assertTrue(exception.getMessage().contains("committed zolt.toml"));
    }

    @Test
    void rejectsUnknownKeys() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [cache]
                        roots = "cache"
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Unknown key `roots` in [cache]"));
    }

    @Test
    void rejectsUnsupportedVersion() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("version = 2\n", tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Unsupported user global config version 2"));
    }

    @Test
    void expandsLeadingTildeAndRejectsInvalidValues() {
        UserGlobalConfig config = new UserGlobalConfigParser().parse("""
                version = 1

                [cache]
                root = "~/.cache/zolt"
                """, tempDir.resolve("config.toml"));

        assertEquals(
                Path.of(System.getProperty("user.home")).resolve(".cache/zolt").toAbsolutePath().normalize(),
                config.cacheRoot());

        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [repository]
                        downloadConcurrency = 0
                        """, tempDir.resolve("config.toml")));
        assertTrue(exception.getMessage().contains("Use a positive integer"));
    }
}
