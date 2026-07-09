package sh.zolt.config;

import java.util.Map;

public record UserGlobalConfigSources(
        String version,
        String cacheRoot,
        String repositoryDownloadConcurrency,
        String repositoryExecutionLane,
        Map<String, RepositoryOverlayConfigSource> repositoryOverlays,
        String javaToolchainDefault,
        String uiColor,
        String uiProgress) {
    public static final String BUILT_IN_DEFAULT = "built-in default";
    public static final String USER_GLOBAL_CONFIG = "user global config";

    public UserGlobalConfigSources {
        repositoryOverlays = Map.copyOf(repositoryOverlays);
    }

    public static UserGlobalConfigSources defaults() {
        return new UserGlobalConfigSources(
                BUILT_IN_DEFAULT,
                BUILT_IN_DEFAULT,
                BUILT_IN_DEFAULT,
                BUILT_IN_DEFAULT,
                Map.of("mavenLocal", RepositoryOverlayConfigSource.defaults()),
                BUILT_IN_DEFAULT,
                BUILT_IN_DEFAULT,
                BUILT_IN_DEFAULT);
    }
}
