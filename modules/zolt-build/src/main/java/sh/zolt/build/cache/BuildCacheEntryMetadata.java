package sh.zolt.build.cache;

import java.time.Instant;
import java.util.Optional;

/**
 * Sidecar metadata stored next to a cache archive, OUTSIDE the hashed archive content.
 *
 * <p>Keeping the created-at timestamp and integrity hash out of the archive keeps the archive itself
 * byte-reproducible (sorted entries, epoch-0 timestamps) while still recording provenance and an
 * integrity check. The {@code sha256} is the hash of the archive file; a mismatch on restore means the
 * blob is corrupt or partially written, and the entry is discarded and rebuilt.
 */
public record BuildCacheEntryMetadata(
        String key,
        String scope,
        String zoltVersion,
        Instant createdAt,
        String sha256,
        long sizeBytes) {

    private static final String VERSION = "1";

    /** A specific way a sidecar can fail to describe a requested entry and its blob. */
    public enum Mismatch {
        KEY("key"),
        SCOPE("scope"),
        SIZE("size"),
        SHA("sha256");

        private final String label;

        Mismatch(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    /**
     * Verify this sidecar actually describes {@code requestedKey} and the blob whose size and hash are
     * given, returning the first field that fails or empty when the key, scope, declared size, and
     * integrity hash all match. Callers treat any mismatch as a miss and never touch build output: this
     * closes the gap where a restore trusted the archive SHA alone and ignored whether the sidecar even
     * belonged to the requested entry.
     */
    public Optional<Mismatch> mismatchAgainst(BuildCacheKey requestedKey, long actualSizeBytes, String actualSha256) {
        if (!requestedKey.hash().equals(key)) {
            return Optional.of(Mismatch.KEY);
        }
        if (!requestedKey.scope().id().equals(scope)) {
            return Optional.of(Mismatch.SCOPE);
        }
        if (sizeBytes != actualSizeBytes) {
            return Optional.of(Mismatch.SIZE);
        }
        if (actualSha256 == null || !sha256.equalsIgnoreCase(actualSha256)) {
            return Optional.of(Mismatch.SHA);
        }
        return Optional.empty();
    }

    public String format() {
        return "version=" + VERSION + '\n'
                + "key=" + key + '\n'
                + "scope=" + scope + '\n'
                + "zoltVersion=" + zoltVersion + '\n'
                + "createdAt=" + createdAt + '\n'
                + "sha256=" + sha256 + '\n'
                + "sizeBytes=" + sizeBytes + '\n';
    }

    public static Optional<BuildCacheEntryMetadata> parse(String text) {
        String key = null;
        String scope = null;
        String zoltVersion = "";
        Instant createdAt = null;
        String sha256 = null;
        long sizeBytes = 0L;
        for (String line : text.lines().toList()) {
            int separator = line.indexOf('=');
            if (separator < 0) {
                continue;
            }
            String name = line.substring(0, separator);
            String value = line.substring(separator + 1);
            switch (name) {
                case "key" -> key = value;
                case "scope" -> scope = value;
                case "zoltVersion" -> zoltVersion = value;
                case "createdAt" -> createdAt = parseInstant(value);
                case "sha256" -> sha256 = value;
                case "sizeBytes" -> sizeBytes = parseLong(value);
                default -> {
                    // Ignore unknown/forward-compatible fields (including version).
                }
            }
        }
        if (key == null || scope == null || sha256 == null || sha256.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(new BuildCacheEntryMetadata(
                key, scope, zoltVersion, createdAt == null ? Instant.EPOCH : createdAt, sha256, sizeBytes));
    }

    private static Instant parseInstant(String value) {
        try {
            return Instant.parse(value);
        } catch (RuntimeException exception) {
            return Instant.EPOCH;
        }
    }

    private static long parseLong(String value) {
        try {
            return Long.parseLong(value.strip());
        } catch (NumberFormatException exception) {
            return 0L;
        }
    }
}
