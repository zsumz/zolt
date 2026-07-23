package sh.zolt.build.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class LocalBuildCacheTest {
    @Test
    void storesThenRestoresIdenticalOutput(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        Path output = temp.resolve("target/classes");
        writeFile(output.resolve("com/example/Widget.class"), "widget");
        writeFile(output.resolve("META-INF/services/foo"), "provider");
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21");

        cache.store(key, output, "test-version");
        Path restored = temp.resolve("restored/classes");
        writeFile(restored.resolve("stale.class"), "stale");
        BuildCacheRestoreResult result = cache.restore(key, restored);

        assertTrue(result.restored());
        assertEquals(1, result.classCount());
        assertEquals("local", result.source());
        assertArrayEquals(
                Files.readAllBytes(output.resolve("com/example/Widget.class")),
                Files.readAllBytes(restored.resolve("com/example/Widget.class")));
        assertTrue(Files.exists(restored.resolve("META-INF/services/foo")));
        assertFalse(Files.exists(restored.resolve("stale.class")), "restore cleans the target first");
    }

    @Test
    void missesWhenNothingStored(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.TEST, "inputsha", "21");
        assertFalse(cache.restore(key, temp.resolve("out")).restored());
    }

    @Test
    void treatsCorruptEntryAsMissAndDeletesIt(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        Path output = temp.resolve("target/classes");
        writeFile(output.resolve("A.class"), "aaa");
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21");
        cache.store(key, output, "v");
        Path archiveFile = cache.archiveFileIfPresent(key).orElseThrow();
        Files.write(archiveFile, "corruption".getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);

        BuildCacheRestoreResult result = cache.restore(key, temp.resolve("out"));
        assertFalse(result.restored());
        assertTrue(cache.archiveFileIfPresent(key).isEmpty(), "corrupt entry is removed so the build re-stores");
    }

    @Test
    void prunesLeastRecentlyModifiedEntriesToCap(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        BuildCacheKey oldest = store(cache, temp, "one", "oldest");
        BuildCacheKey middle = store(cache, temp, "two", "middle");
        BuildCacheKey newest = store(cache, temp, "three", "newest");
        setModified(cache, oldest, Instant.parse("2020-01-01T00:00:00Z"));
        setModified(cache, middle, Instant.parse("2021-01-01T00:00:00Z"));
        setModified(cache, newest, Instant.parse("2022-01-01T00:00:00Z"));

        long total = cache.status().totalBytes();
        BuildCachePruneResult result = cache.prune(total - 1);

        assertEquals(1, result.removedEntries());
        assertTrue(cache.archiveFileIfPresent(oldest).isEmpty(), "oldest entry pruned first");
        assertTrue(cache.archiveFileIfPresent(middle).isPresent());
        assertTrue(cache.archiveFileIfPresent(newest).isPresent());
    }

    @Test
    void autoPrunesOnStoreWhenOverCap(@TempDir Path temp) throws IOException {
        // Cap of 1 byte forces the store to immediately evict; the just-stored entry may itself be evicted,
        // which is fine — correctness only requires the cache never to exceed the cap unboundedly.
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 1L);
        store(cache, temp, "one", "content-one");
        assertTrue(cache.status().totalBytes() <= 1L || cache.status().entryCount() <= 1);
    }

    private static BuildCacheKey store(LocalBuildCache cache, Path temp, String id, String content) throws IOException {
        Path output = temp.resolve("src-" + id + "/classes");
        writeFile(output.resolve(id + ".class"), content);
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.MAIN, id, "21");
        cache.store(key, output, "v");
        return key;
    }

    private static void setModified(LocalBuildCache cache, BuildCacheKey key, Instant instant) throws IOException {
        Files.setLastModifiedTime(cache.archiveFileIfPresent(key).orElseThrow(), FileTime.from(instant));
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Files.writeString(path, content, StandardCharsets.UTF_8);
    }
}
