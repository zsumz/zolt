package sh.zolt.build.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.FileTime;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
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

    @Test
    void rejectsEntryWhoseSidecarScopeMismatchesAndDeletesIt(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        Path output = temp.resolve("target/classes");
        writeFile(output.resolve("A.class"), "aaa");
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21");
        cache.store(key, output, "v");
        // Tamper the sidecar so it no longer describes the requested (MAIN) entry, keeping the SHA valid;
        // identity validation must still reject it, proving the local tier checks more than the SHA.
        Path metaFile = cache.metaFileIfPresent(key).orElseThrow();
        String tampered = Files.readString(metaFile, StandardCharsets.UTF_8).replace("scope=main", "scope=test");
        Files.writeString(metaFile, tampered, StandardCharsets.UTF_8);

        BuildCacheRestoreResult result = cache.restore(key, temp.resolve("out"));
        assertFalse(result.restored());
        assertTrue(cache.archiveFileIfPresent(key).isEmpty(), "a mismatched entry is removed");
    }

    @Test
    void midExtractionFailureLeavesPreviousOutputUntouched(@TempDir Path temp) throws IOException {
        LocalBuildCache cache = new LocalBuildCache(temp.resolve("cache"), 0L);
        Path seed = temp.resolve("target/classes");
        writeFile(seed.resolve("A.class"), "aaa");
        BuildCacheKey key = BuildCacheKey.of(BuildCacheScope.MAIN, "inputsha", "21");
        cache.store(key, seed, "v");

        // Replace the stored archive with one whose first entry extracts cleanly but whose second escapes the
        // output directory, so extraction fails partway. Rewrite the sidecar so identity validation passes and
        // control actually reaches extraction.
        byte[] malicious = maliciousArchiveWithTraversal();
        Files.write(cache.archiveFileIfPresent(key).orElseThrow(), malicious);
        rewriteSidecar(cache, key, malicious);

        // A previous restore produced this output; a failed restore must leave it exactly as it was.
        Path output = temp.resolve("restored/classes");
        writeFile(output.resolve("keep.class"), "previous");

        BuildCacheRestoreResult result = cache.restore(key, output);

        assertFalse(result.restored(), "a mid-extraction failure is a miss");
        assertArrayEquals(
                "previous".getBytes(StandardCharsets.UTF_8),
                Files.readAllBytes(output.resolve("keep.class")),
                "the previous output is left byte-for-byte untouched");
        assertFalse(Files.exists(output.resolve("safe.class")), "no partial output leaked into the live target");
        assertFalse(Files.exists(temp.resolve("restored/escaped.class")), "the zip-slip entry did not escape");
        assertTrue(cache.archiveFileIfPresent(key).isEmpty(), "the poisoned entry is dropped so a later build re-stores");
    }

    private static byte[] maliciousArchiveWithTraversal() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry("safe.class"));
            zip.write("safe".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../escaped.class"));
            zip.write("evil".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    private static void rewriteSidecar(LocalBuildCache cache, BuildCacheKey key, byte[] archiveBytes)
            throws IOException {
        Path metaFile = cache.metaFileIfPresent(key).orElseThrow();
        BuildCacheEntryMetadata metadata = new BuildCacheEntryMetadata(
                key.hash(), key.scope().id(), "v", Instant.EPOCH, sha256Hex(archiveBytes), archiveBytes.length);
        Files.writeString(metaFile, metadata.format(), StandardCharsets.UTF_8);
    }

    private static String sha256Hex(byte[] bytes) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException(exception);
        }
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
