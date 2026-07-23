package sh.zolt.build.cache;

import java.nio.file.Path;
import java.util.Objects;

/**
 * Resolved build-output cache settings for one invocation.
 *
 * <p>This is a machine concern (see the user-global config and the CA-bundle precedent): the CLI
 * translates {@code [buildCache]} from {@code ~/.zolt/config.toml} into this carrier and injects it
 * into the build. {@code zolt-build} deliberately does not depend on the config module. A disabled
 * value makes the build behave exactly as it did before the cache existed.
 */
public record BuildCacheSettings(boolean enabled, Path directory, long maxSizeBytes) {
    public BuildCacheSettings {
        if (enabled) {
            Objects.requireNonNull(directory, "directory");
        }
        maxSizeBytes = Math.max(0L, maxSizeBytes);
    }

    public static BuildCacheSettings disabled() {
        return new BuildCacheSettings(false, null, 0L);
    }
}
