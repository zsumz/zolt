package sh.zolt.cli.command;

import sh.zolt.build.cache.BuildCacheService;
import sh.zolt.build.cache.BuildCacheSettings;
import sh.zolt.build.cache.RemoteBuildCacheClient;
import sh.zolt.cli.CommandHumanOutput;
import sh.zolt.cli.ZoltCli;
import sh.zolt.cli.net.CommandNetwork;
import sh.zolt.config.BuildCacheConfig;
import sh.zolt.config.RemoteBuildCacheConfig;
import sh.zolt.config.UserGlobalConfig;
import sh.zolt.config.UserGlobalConfigException;
import sh.zolt.config.UserGlobalConfigParser;
import sh.zolt.maven.repository.RepositoryAuthentication;
import sh.zolt.project.RepositoryCredentialSettings;
import sh.zolt.project.RepositoryUrlPolicy;
import java.net.URI;
import java.util.Optional;

/**
 * Resolves the build-output cache for a command from the user-global {@code [buildCache]} config,
 * honoring {@code --no-build-cache} and {@code --offline}.
 *
 * <p>The cache is opt-in and a machine concern; it must never block a build, so a broken config or an
 * unresolvable/invalid remote degrades to a disabled or local-only cache rather than an error (mirroring
 * {@link CommandNetwork}). Offline drops the remote tier while the local cache still serves.
 */
public final class CommandBuildCache {
    private static final String PUSH_ENV = "ZOLT_BUILD_CACHE_PUSH";

    private CommandBuildCache() {
    }

    /** The cache service for a command; disabled when the flag is set or the config does not enable it. */
    public static BuildCacheService service(boolean disabledByFlag, boolean offline) {
        if (disabledByFlag) {
            return BuildCacheService.disabled();
        }
        UserGlobalConfig config = readConfig();
        BuildCacheConfig buildCache = config.buildCache();
        if (!buildCache.enabled() || buildCache.directory().isEmpty()) {
            return BuildCacheService.disabled();
        }
        BuildCacheSettings settings =
                new BuildCacheSettings(true, buildCache.directory().orElseThrow(), buildCache.maxSizeBytes());
        Optional<RemoteBuildCacheClient> remote = offline
                ? Optional.empty()
                : remoteClient(config, buildCache.remote());
        return BuildCacheService.create(settings, remote, ZoltCli.version());
    }

    /** Print any accumulated build-cache warnings (e.g. a rejected remote push) once, after the build. */
    public static void surfaceWarnings(CommandHumanOutput output, BuildCacheService service) {
        for (String warning : service.drainWarnings()) {
            output.statusDetail("warning", warning);
        }
    }

    /** Resolved local settings from {@code ~/.zolt/config.toml [buildCache]}; disabled on any read error. */
    public static BuildCacheSettings settings() {
        BuildCacheConfig config = readConfig().buildCache();
        if (!config.enabled() || config.directory().isEmpty()) {
            return BuildCacheSettings.disabled();
        }
        return new BuildCacheSettings(true, config.directory().orElseThrow(), config.maxSizeBytes());
    }

    private static Optional<RemoteBuildCacheClient> remoteClient(
            UserGlobalConfig config,
            Optional<RemoteBuildCacheConfig> remoteConfig) {
        if (remoteConfig.isEmpty()) {
            return Optional.empty();
        }
        RemoteBuildCacheConfig remote = remoteConfig.orElseThrow();
        URI uri;
        try {
            // Reuse the repository URL policy: reject embedded userinfo and require HTTPS for a
            // credentialed remote. On a policy violation, degrade to local-only rather than leak or fail.
            uri = RepositoryUrlPolicy.requireSafeUrl(
                    "build cache remote", remote.url(), remote.credentials().isPresent());
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        Optional<RepositoryAuthentication> authentication = resolveAuthentication(config, remote.credentials());
        boolean push = remote.push() || pushEnabledByEnv();
        return Optional.of(new RemoteBuildCacheClient(
                CommandNetwork.defaultTransport().newHttpClient(), uri, authentication, push));
    }

    private static Optional<RepositoryAuthentication> resolveAuthentication(
            UserGlobalConfig config,
            Optional<String> credentialId) {
        if (credentialId.isEmpty()) {
            return Optional.empty();
        }
        RepositoryCredentialSettings credential = config.repositoryCredentials().get(credentialId.orElseThrow());
        if (credential == null) {
            return Optional.empty();
        }
        if (credential.usesToken()) {
            String token = System.getenv(credential.tokenEnv().orElseThrow());
            return blank(token) ? Optional.empty() : Optional.of(RepositoryAuthentication.bearer(token));
        }
        String username = System.getenv(credential.usernameEnv().orElseThrow());
        String password = System.getenv(credential.passwordEnv().orElseThrow());
        if (blank(username) || blank(password)) {
            return Optional.empty();
        }
        return Optional.of(new RepositoryAuthentication(username, password));
    }

    private static boolean pushEnabledByEnv() {
        String value = System.getenv(PUSH_ENV);
        return value != null && (value.equals("1") || value.equalsIgnoreCase("true"));
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private static UserGlobalConfig readConfig() {
        try {
            return new UserGlobalConfigParser().read(CommandNetwork.defaultConfigPath());
        } catch (UserGlobalConfigException exception) {
            return UserGlobalConfig.defaults(CommandNetwork.defaultConfigPath());
        }
    }
}
