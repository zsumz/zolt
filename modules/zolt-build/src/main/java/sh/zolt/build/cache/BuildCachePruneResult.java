package sh.zolt.build.cache;

/** Outcome of an LRU prune of the local build cache. */
public record BuildCachePruneResult(int removedEntries, long freedBytes, long remainingBytes) {
    public static BuildCachePruneResult none(long remainingBytes) {
        return new BuildCachePruneResult(0, 0L, remainingBytes);
    }
}
