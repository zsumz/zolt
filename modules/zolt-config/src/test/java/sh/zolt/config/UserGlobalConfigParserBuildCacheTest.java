package sh.zolt.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class UserGlobalConfigParserBuildCacheTest {
    @TempDir
    Path tempDir;

    @Test
    void parsesBuildCacheSection() throws IOException {
        Path configPath = tempDir.resolve("zolt/config.toml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                version = 1

                [buildCache]
                enabled = true
                dir = "build-cache"
                maxSizeMb = 512
                """);

        UserGlobalConfig config = new UserGlobalConfigParser().read(configPath);

        assertTrue(config.buildCache().enabled());
        assertEquals(
                configPath.getParent().resolve("build-cache").toAbsolutePath().normalize(),
                config.buildCache().directory().orElseThrow());
        assertEquals(512L * 1024L * 1024L, config.buildCache().maxSizeBytes());
    }

    @Test
    void buildCacheEnabledDefaultsDirAndCap() throws IOException {
        Path configPath = tempDir.resolve("zolt/config.toml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                version = 1

                [buildCache]
                enabled = true
                """);

        UserGlobalConfig config = new UserGlobalConfigParser().read(configPath);

        assertTrue(config.buildCache().enabled());
        assertTrue(config.buildCache().directory().orElseThrow().endsWith(Path.of(".zolt", "build-cache")));
        assertEquals(2048L * 1024L * 1024L, config.buildCache().maxSizeBytes());
    }

    @Test
    void buildCacheAbsentOrDisabledIsOff() {
        UserGlobalConfig absent = new UserGlobalConfigParser().read(tempDir.resolve("absent.toml"));
        assertFalse(absent.buildCache().enabled());
        assertTrue(absent.buildCache().directory().isEmpty());
    }

    @Test
    void parsesBuildCacheRemoteAndUserLevelCredentials() throws IOException {
        Path configPath = tempDir.resolve("zolt/config.toml");
        Files.createDirectories(configPath.getParent());
        Files.writeString(configPath, """
                version = 1

                [buildCache]
                enabled = true

                [buildCache.remote]
                url = "https://cache.example.com/generic"
                credentials = "cache"
                push = true

                [repositoryCredentials.cache]
                tokenEnv = "CACHE_TOKEN"
                """);

        UserGlobalConfig config = new UserGlobalConfigParser().read(configPath);

        assertTrue(config.buildCache().remote().isPresent());
        assertEquals("https://cache.example.com/generic", config.buildCache().remote().orElseThrow().url());
        assertEquals("cache", config.buildCache().remote().orElseThrow().credentials().orElseThrow());
        assertTrue(config.buildCache().remote().orElseThrow().push());
        assertTrue(config.repositoryCredentials().get("cache").usesToken());
    }

    @Test
    void rejectsBuildCacheRemoteReferencingUndefinedCredential() {
        Path configPath = tempDir.resolve("zolt/config.toml");
        assertThrows(UserGlobalConfigException.class, () -> {
            Files.createDirectories(configPath.getParent());
            Files.writeString(configPath, """
                    version = 1

                    [buildCache]
                    enabled = true

                    [buildCache.remote]
                    url = "https://cache.example.com/generic"
                    credentials = "missing"
                    """);
            new UserGlobalConfigParser().read(configPath);
        });
    }
}
