package sh.zolt.config;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Machine-level build-output cache settings from {@code [buildCache]} in the user global config.
 *
 * <p>The cache is opt-in and a machine concern, not a build input: enabling it, its location, and its
 * size cap never affect compiled bytes or {@code zolt.lock}, so it lives here rather than in a committed
 * {@code zolt.toml} (mirroring the CA-bundle/network precedent). A disabled value is the default and
 * makes every build behave as it did before the cache existed.
 *
 * @param directory the resolved cache directory when enabled, empty when disabled
 * @param maxSizeBytes the size cap the cache is pruned to (LRU), in bytes
 */
public record BuildCacheConfig(
        boolean enabled,
        Optional<Path> directory,
        long maxSizeBytes,
        Optional<RemoteBuildCacheConfig> remote) {
    public BuildCacheConfig {
        directory = directory == null ? Optional.empty() : directory;
        maxSizeBytes = Math.max(0L, maxSizeBytes);
        remote = remote == null ? Optional.empty() : remote;
    }

    public static BuildCacheConfig disabled() {
        return new BuildCacheConfig(false, Optional.empty(), 0L, Optional.empty());
    }
}
