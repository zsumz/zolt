package sh.zolt.build.cache;

import java.nio.file.Path;

/** Snapshot of the local build cache for {@code zolt cache status}. */
public record BuildCacheStatus(Path directory, int entryCount, long totalBytes, long maxSizeBytes) {
}
