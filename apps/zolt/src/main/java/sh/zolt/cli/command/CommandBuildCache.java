package sh.zolt.cli.command;

import sh.zolt.build.cache.BuildCacheService;
import sh.zolt.build.cache.BuildCacheSettings;
import sh.zolt.cli.ZoltCli;
import sh.zolt.cli.net.CommandNetwork;
import sh.zolt.config.BuildCacheConfig;
import sh.zolt.config.UserGlobalConfigException;
import sh.zolt.config.UserGlobalConfigParser;

/**
 * Resolves the build-output cache for a command from the user-global {@code [buildCache]} config,
 * honoring the {@code --no-build-cache} override.
 *
 * <p>The cache is opt-in and a machine concern; it must never block a build, so a broken config
 * degrades to a disabled cache rather than an error (mirroring {@link CommandNetwork}).
 */
public final class CommandBuildCache {
    private CommandBuildCache() {
    }

    /** The cache service for a command; disabled when the flag is set or the config does not enable it. */
    public static BuildCacheService service(boolean disabledByFlag) {
        if (disabledByFlag) {
            return BuildCacheService.disabled();
        }
        return BuildCacheService.create(settings(), ZoltCli.version());
    }

    /** Resolved settings from {@code ~/.zolt/config.toml [buildCache]}; disabled on any read error. */
    public static BuildCacheSettings settings() {
        BuildCacheConfig config = readConfig();
        if (!config.enabled() || config.directory().isEmpty()) {
            return BuildCacheSettings.disabled();
        }
        return new BuildCacheSettings(true, config.directory().orElseThrow(), config.maxSizeBytes());
    }

    private static BuildCacheConfig readConfig() {
        try {
            return new UserGlobalConfigParser().read(CommandNetwork.defaultConfigPath()).buildCache();
        } catch (UserGlobalConfigException exception) {
            return BuildCacheConfig.disabled();
        }
    }
}
