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
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().cacheRoot());
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().repositoryDownloadConcurrency());
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
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().cacheRoot());
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().repositoryDownloadConcurrency());
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().repositoryExecutionLane());
        assertEquals(
                UserGlobalConfigSources.USER_GLOBAL_CONFIG,
                config.sources().repositoryOverlays().get("mavenLocal").kind());
        assertEquals(
                UserGlobalConfigSources.USER_GLOBAL_CONFIG,
                config.sources().repositoryOverlays().get("mavenLocal").enabled());
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().uiColor());
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().uiProgress());
    }

    @Test
    void tracksBuiltInSourcesForOmittedKeysInExistingConfig() {
        UserGlobalConfig config = new UserGlobalConfigParser().parse("""
                version = 1

                [repository]
                downloadConcurrency = 2

                [repositoryOverlays.mavenLocal]
                enabled = true
                """, tempDir.resolve("config.toml"));

        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().version());
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().cacheRoot());
        assertEquals(UserGlobalConfigSources.USER_GLOBAL_CONFIG, config.sources().repositoryDownloadConcurrency());
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().repositoryExecutionLane());
        assertEquals(
                UserGlobalConfigSources.BUILT_IN_DEFAULT,
                config.sources().repositoryOverlays().get("mavenLocal").kind());
        assertEquals(
                UserGlobalConfigSources.USER_GLOBAL_CONFIG,
                config.sources().repositoryOverlays().get("mavenLocal").enabled());
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().uiColor());
        assertEquals(UserGlobalConfigSources.BUILT_IN_DEFAULT, config.sources().uiProgress());
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
    void mergesMultipleRepositoryOverlaysOverDefaults() {
        UserGlobalConfig config = new UserGlobalConfigParser().parse("""
                version = 1

                [repositoryOverlays.mavenLocal]
                kind = "maven-local"
                enabled = true

                [repositoryOverlays.corporate]
                kind = "maven-local"
                enabled = false
                """, tempDir.resolve("config.toml"));

        RepositoryOverlayConfig mavenLocal = config.repositoryOverlays().get("mavenLocal");
        assertEquals("maven-local", mavenLocal.kind());
        assertTrue(mavenLocal.enabled());

        RepositoryOverlayConfig corporate = config.repositoryOverlays().get("corporate");
        assertEquals("corporate", corporate.id());
        assertEquals("maven-local", corporate.kind());
        assertFalse(corporate.enabled());

        assertEquals(
                UserGlobalConfigSources.USER_GLOBAL_CONFIG,
                config.sources().repositoryOverlays().get("corporate").enabled());
    }

    @Test
    void rejectsInvalidOverlayKind() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [repositoryOverlays.mavenLocal]
                        kind = "bogus"
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("[repositoryOverlays.mavenLocal].kind"));
        assertTrue(exception.getMessage().contains("Use one of"));
        assertTrue(exception.getMessage().contains("maven-local"));
    }

    @Test
    void rejectsUnknownKeyUnderOverlay() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [repositoryOverlays.mavenLocal]
                        enabled = true
                        priority = 5
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Unknown key `priority` in [repositoryOverlays.mavenLocal]"));
    }

    // NOTE: The "Use a table with kind and enabled keys" branch in
    // UserGlobalConfigParser.repositoryOverlays() guards against table.getTable(id)
    // returning null. With tomlj 1.1.1, a present overlay key of the wrong shape (scalar,
    // array, etc.) makes getTable(...) throw TomlInvalidTypeException before that null check,
    // and getTable only returns null for an ABSENT key (which never appears in keySet()).
    // The branch is therefore unreachable from parse input today, so it is intentionally not
    // asserted here rather than coercing the parser's visibility/behavior to cover dead code.

    @Test
    void rejectsPackageSectionWithCommittedTomlRemediation() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [package]
                        mode = "uber"
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Global config section [package] is not supported"));
        assertTrue(exception.getMessage().contains("committed zolt.toml"));
    }

    @Test
    void rejectsDependenciesSectionWithCommittedTomlRemediation() {
        UserGlobalConfigException exception = assertThrows(
                UserGlobalConfigException.class,
                () -> new UserGlobalConfigParser().parse("""
                        version = 1

                        [dependencies]
                        "org.example:lib" = "1.0.0"
                        """, tempDir.resolve("config.toml")));

        assertTrue(exception.getMessage().contains("Global config section [dependencies] is not supported"));
        assertTrue(exception.getMessage().contains("committed zolt.toml"));
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
