package sh.zolt.build.cache;

/**
 * Outcome of a build-output cache restore attempt.
 *
 * @param restored   whether the module's compiled output was restored from the cache
 * @param classCount number of {@code .class} files restored (for observability), zero on a miss
 * @param source     where the hit came from ({@code "local"} or {@code "remote"}), empty on a miss
 */
public record BuildCacheRestoreResult(boolean restored, int classCount, String source) {
    public BuildCacheRestoreResult {
        source = source == null ? "" : source;
        classCount = Math.max(0, classCount);
    }

    public static BuildCacheRestoreResult miss() {
        return new BuildCacheRestoreResult(false, 0, "");
    }

    public static BuildCacheRestoreResult restoredFrom(String source, int classCount) {
        return new BuildCacheRestoreResult(true, classCount, source);
    }
}
