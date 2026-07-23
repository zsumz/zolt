package sh.zolt.maven.metadata;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class MetadataCacheTest {
    private static final byte[] LISTING =
            "<metadata><versioning><versions><version>1.0.0</version></versions></versioning></metadata>"
                    .getBytes(StandardCharsets.UTF_8);

    @TempDir
    Path cacheRoot;

    @Test
    void writesUnderSeparateMetadataNamespace() {
        new MetadataCache(cacheRoot).write("central", "com.google.guava", "guava", LISTING);

        Path expected = cacheRoot
                .resolve("metadata")
                .resolve("central")
                .resolve("com/google/guava")
                .resolve("guava")
                .resolve("maven-metadata.xml");
        assertTrue(Files.isRegularFile(expected));
    }

    @Test
    void roundTripsListingBytes() {
        MetadataCache cache = new MetadataCache(cacheRoot);
        cache.write("central", "com.example", "lib", LISTING);

        Optional<byte[]> read = cache.read("central", "com.example", "lib");
        assertTrue(read.isPresent());
        assertArrayEquals(LISTING, read.orElseThrow());
    }

    @Test
    void readReturnsEmptyWhenAbsent() {
        assertFalse(new MetadataCache(cacheRoot).read("central", "com.example", "absent").isPresent());
    }

    @Test
    void recordsFetchedTimestampSidecar() {
        Instant now = Instant.parse("2026-07-23T12:00:00Z");
        MetadataCache cache = new MetadataCache(cacheRoot, Clock.fixed(now, ZoneOffset.UTC));
        cache.write("central", "com.example", "lib", LISTING);

        assertEquals(Optional.of(now), cache.fetchedAt("central", "com.example", "lib"));
    }

    @Test
    void isolatesRepositories() {
        MetadataCache cache = new MetadataCache(cacheRoot);
        cache.write("alpha", "com.example", "lib", LISTING);

        assertTrue(cache.read("alpha", "com.example", "lib").isPresent());
        assertFalse(cache.read("zeta", "com.example", "lib").isPresent());
    }
}
