package sh.zolt.config;

import java.util.Optional;

/**
 * Machine-level remote build-cache settings from {@code [buildCache.remote]} in the user global config.
 *
 * <p>A dumb HTTP endpoint (Artifactory/Nexus generic repository): entries are read with GET and, when
 * {@code push} is enabled, written with PUT. {@code credentials} names a {@code [repositoryCredentials]}
 * block (env-var references only, never secrets). Reads are the default; pushing is opt-in so developers
 * populate from a shared cache while CI writes to it.
 */
public record RemoteBuildCacheConfig(String url, Optional<String> credentials, boolean push) {
    public RemoteBuildCacheConfig {
        credentials = credentials == null ? Optional.empty() : credentials;
    }
}
