package sh.zolt.build.cache;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Facade the build orchestrator uses to restore and store compile outputs. It hides the storage tiers
 * (local content-addressed store now; a remote HTTP layer later) and enforces the discipline that the
 * cache must never fail a build: a restore problem degrades to a miss (rebuild), and a store problem is
 * a best-effort no-op. Correctness beats hit rate — any doubt is a miss.
 *
 * <p>A {@linkplain #disabled() disabled} instance is a total no-op, so the default build path behaves
 * exactly as it did before the cache existed. The CLI injects an enabled instance built from the
 * user-global {@code [buildCache]} config.
 */
public final class BuildCacheService {
    private final boolean enabled;
    private final LocalBuildCache local;
    private final String zoltVersion;

    private BuildCacheService(boolean enabled, LocalBuildCache local, String zoltVersion) {
        this.enabled = enabled;
        this.local = local;
        this.zoltVersion = zoltVersion == null ? "" : zoltVersion;
    }

    public static BuildCacheService disabled() {
        return new BuildCacheService(false, null, "");
    }

    public static BuildCacheService create(BuildCacheSettings settings, String zoltVersion) {
        if (!settings.enabled()) {
            return disabled();
        }
        return new BuildCacheService(
                true,
                new LocalBuildCache(settings.directory(), settings.maxSizeBytes()),
                zoltVersion);
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<LocalBuildCache> localCache() {
        return Optional.ofNullable(local);
    }

    /** Attempt to restore the module output for {@code key}; a miss (including on any error) rebuilds. */
    public BuildCacheRestoreResult restore(BuildCacheKey key, Path outputDirectory) {
        if (!enabled) {
            return BuildCacheRestoreResult.miss();
        }
        try {
            return local.restore(key, outputDirectory);
        } catch (IOException | RuntimeException exception) {
            return BuildCacheRestoreResult.miss();
        }
    }

    /** Store the module output for {@code key}; best-effort, never fails the build. */
    public void store(BuildCacheKey key, Path outputDirectory) {
        if (!enabled) {
            return;
        }
        try {
            local.store(key, outputDirectory, zoltVersion);
        } catch (IOException | RuntimeException exception) {
            // Storing is an optimization; a failure only means a future build recompiles.
        }
    }
}
