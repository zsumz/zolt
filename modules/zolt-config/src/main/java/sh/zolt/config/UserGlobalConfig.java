package sh.zolt.config;

import java.nio.file.Path;
import java.util.Map;

public record UserGlobalConfig(
        int version,
        Path configPath,
        Path cacheRoot,
        RepositoryExecutionConfig repository,
        Map<String, RepositoryOverlayConfig> repositoryOverlays,
        UserGlobalToolchainDefaults toolchainDefaults,
        UiConfig ui,
        NetworkConfig network,
        BuildCacheConfig buildCache,
        UserGlobalConfigSources sources) {
    public UserGlobalConfig {
        repositoryOverlays = Map.copyOf(repositoryOverlays);
    }

    public static UserGlobalConfig defaults(Path configPath) {
        return new UserGlobalConfig(
                1,
                configPath,
                UserGlobalConfigParser.expandUserHome(Path.of("~/.zolt/cache")),
                new RepositoryExecutionConfig(8, "platform"),
                Map.of("mavenLocal", new RepositoryOverlayConfig("mavenLocal", "maven-local", false)),
                UserGlobalToolchainDefaults.none(),
                new UiConfig("auto", "auto"),
                NetworkConfig.none(),
                BuildCacheConfig.disabled(),
                UserGlobalConfigSources.defaults());
    }
}
