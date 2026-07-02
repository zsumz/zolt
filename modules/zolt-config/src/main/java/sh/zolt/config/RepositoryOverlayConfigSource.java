package sh.zolt.config;

public record RepositoryOverlayConfigSource(String kind, String enabled) {
    public static RepositoryOverlayConfigSource defaults() {
        return new RepositoryOverlayConfigSource(
                UserGlobalConfigSources.BUILT_IN_DEFAULT,
                UserGlobalConfigSources.BUILT_IN_DEFAULT);
    }
}
