package sh.zolt.build.cache;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Facade the build orchestrator uses to restore and store compile outputs across a local
 * content-addressed store and, optionally, a remote HTTP tier.
 *
 * <p>The cache must never fail a build: a restore problem degrades to a miss (rebuild), and a store
 * problem is a best-effort no-op that at most surfaces a warning. Correctness beats hit rate — any
 * doubt is a miss. A {@linkplain #disabled() disabled} instance is a total no-op, so the default build
 * path behaves exactly as it did before the cache existed.
 *
 * <p>Restore order is local, then remote: a remote hit is verified, copied into the local store, and
 * restored from there. Store writes locally and, only when the remote is configured to push, uploads.
 * The remote is absent (never consulted) when the caller is offline.
 */
public final class BuildCacheService {
    /**
     * Metadata sidecar cap: a handful of short {@code name=value} lines. Small and fixed so a hostile
     * remote cannot make the client buffer an unbounded "sidecar".
     */
    private static final long MAX_METADATA_BYTES = 64L * 1024;

    /** Archive download cap when no cache size cap is configured (an unlimited local cache). */
    private static final long DEFAULT_ARCHIVE_LIMIT_BYTES = 512L * 1024 * 1024;

    private final boolean enabled;
    private final LocalBuildCache local;
    private final Optional<RemoteBuildCacheClient> remote;
    private final String zoltVersion;
    private final long maxSizeBytes;
    private final Set<String> warnings = Collections.synchronizedSet(new LinkedHashSet<>());

    private BuildCacheService(
            boolean enabled,
            LocalBuildCache local,
            Optional<RemoteBuildCacheClient> remote,
            String zoltVersion,
            long maxSizeBytes) {
        this.enabled = enabled;
        this.local = local;
        this.remote = remote == null ? Optional.empty() : remote;
        this.zoltVersion = zoltVersion == null ? "" : zoltVersion;
        this.maxSizeBytes = Math.max(0L, maxSizeBytes);
    }

    public static BuildCacheService disabled() {
        return new BuildCacheService(false, null, Optional.empty(), "", 0L);
    }

    public static BuildCacheService create(BuildCacheSettings settings, String zoltVersion) {
        return create(settings, Optional.empty(), zoltVersion);
    }

    public static BuildCacheService create(
            BuildCacheSettings settings,
            Optional<RemoteBuildCacheClient> remote,
            String zoltVersion) {
        if (!settings.enabled()) {
            return disabled();
        }
        return new BuildCacheService(
                true,
                new LocalBuildCache(settings.directory(), settings.maxSizeBytes()),
                remote,
                zoltVersion,
                settings.maxSizeBytes());
    }

    public boolean enabled() {
        return enabled;
    }

    public Optional<LocalBuildCache> localCache() {
        return Optional.ofNullable(local);
    }

    /** Warnings accumulated during the build (e.g. a failed or unauthorized push), each at most once. */
    public List<String> drainWarnings() {
        synchronized (warnings) {
            List<String> drained = new ArrayList<>(warnings);
            warnings.clear();
            return drained;
        }
    }

    /** Attempt to restore the module output for {@code key}; a miss (including on any error) rebuilds. */
    public BuildCacheRestoreResult restore(BuildCacheKey key, Path outputDirectory) {
        if (!enabled) {
            return BuildCacheRestoreResult.miss();
        }
        try {
            BuildCacheRestoreResult localResult = local.restore(key, outputDirectory);
            if (localResult.restored()) {
                return localResult;
            }
        } catch (IOException | RuntimeException exception) {
            // Fall through to the remote tier; a local problem must not block a remote hit.
        }
        if (remote.isEmpty()) {
            return BuildCacheRestoreResult.miss();
        }
        try {
            if (!fetchIntoLocal(key, remote.orElseThrow())) {
                return BuildCacheRestoreResult.miss();
            }
            BuildCacheRestoreResult restored = local.restore(key, outputDirectory);
            return restored.restored()
                    ? BuildCacheRestoreResult.restoredFrom("remote", restored.classCount())
                    : BuildCacheRestoreResult.miss();
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
            return;
        }
        remote.filter(RemoteBuildCacheClient::push).ifPresent(client -> upload(key, client));
    }

    /**
     * Fetch a remote entry into the local store, but only after it is proven to be the requested entry.
     * Both the sidecar and the archive stream to bounded temp files, and the sidecar's key, scope, declared
     * size, and SHA-256 are all checked against the requested key and the delivered blob before anything is
     * adopted. Any breach or mismatch is a miss that deletes the local copy and surfaces one warning; the
     * subsequent restore then behaves exactly as a plain miss.
     */
    private boolean fetchIntoLocal(BuildCacheKey key, RemoteBuildCacheClient client) throws IOException {
        Path metaTemp = Files.createTempFile("zolt-build-cache-", ".zbc.meta");
        Path blobTemp = Files.createTempFile("zolt-build-cache-", ".zbc");
        try {
            RemoteBuildCacheClient.DownloadOutcome metaOutcome =
                    client.download(key.shardedPath(".zbc.meta"), metaTemp, MAX_METADATA_BYTES);
            if (metaOutcome == RemoteBuildCacheClient.DownloadOutcome.TOO_LARGE) {
                warnOversize(client, "metadata sidecar", MAX_METADATA_BYTES);
                return false;
            }
            if (metaOutcome != RemoteBuildCacheClient.DownloadOutcome.HIT) {
                return false;
            }
            long archiveLimit = maxArchiveBytes();
            RemoteBuildCacheClient.DownloadOutcome blobOutcome =
                    client.download(key.shardedPath(".zbc"), blobTemp, archiveLimit);
            if (blobOutcome == RemoteBuildCacheClient.DownloadOutcome.TOO_LARGE) {
                warnOversize(client, "archive", archiveLimit);
                return false;
            }
            if (blobOutcome != RemoteBuildCacheClient.DownloadOutcome.HIT) {
                return false;
            }
            Optional<BuildCacheEntryMetadata> metadata =
                    BuildCacheEntryMetadata.parse(Files.readString(metaTemp, StandardCharsets.UTF_8));
            if (metadata.isEmpty()) {
                warnRejected(client, key, "its metadata sidecar was unreadable");
                return false;
            }
            long actualSize = Files.size(blobTemp);
            String actualSha = sha256(blobTemp);
            Optional<BuildCacheEntryMetadata.Mismatch> mismatch =
                    metadata.orElseThrow().mismatchAgainst(key, actualSize, actualSha);
            if (mismatch.isPresent()) {
                warnRejected(client, key, "its " + mismatch.orElseThrow().label()
                        + " did not match the requested entry");
                return false;
            }
            local.adopt(key, blobTemp, metadata.orElseThrow());
            return true;
        } finally {
            Files.deleteIfExists(blobTemp);
            Files.deleteIfExists(metaTemp);
        }
    }

    private long maxArchiveBytes() {
        return maxSizeBytes > 0 ? maxSizeBytes : DEFAULT_ARCHIVE_LIMIT_BYTES;
    }

    private void warnOversize(RemoteBuildCacheClient client, String which, long limitBytes) {
        warnings.add("Build cache " + which + " from " + client.baseUri() + " exceeded its " + limitBytes
                + "-byte size limit and was treated as a miss. Next: check the remote for oversized or hostile"
                + " entries, or raise the [buildCache] maxSizeMb cap.");
    }

    private void warnRejected(RemoteBuildCacheClient client, BuildCacheKey key, String reason) {
        warnings.add("Build cache entry " + key.hash() + " from " + client.baseUri() + " was rejected because "
                + reason + "; rebuilding instead. Next: ensure the remote serves only intact, matching entries.");
    }

    private void upload(BuildCacheKey key, RemoteBuildCacheClient client) {
        Optional<Path> archive = local.archiveFileIfPresent(key);
        Optional<Path> meta = local.metaFileIfPresent(key);
        if (archive.isEmpty() || meta.isEmpty()) {
            return;
        }
        RemoteUploadOutcome outcome = client.put(key.shardedPath(".zbc"), archive.orElseThrow());
        if (outcome == RemoteUploadOutcome.UPLOADED) {
            client.put(key.shardedPath(".zbc.meta"), meta.orElseThrow());
            return;
        }
        if (outcome == RemoteUploadOutcome.UNAUTHORIZED) {
            warnings.add("Build cache push was rejected as unauthorized by " + client.baseUri()
                    + ". Check the [buildCache.remote] credentials env vars. Next: unset push or fix the token.");
        } else {
            warnings.add("Build cache push to " + client.baseUri() + " failed; continuing without uploading.");
        }
    }

    private static String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            try (InputStream in = new DigestInputStream(new BufferedInputStream(Files.newInputStream(file)), digest)) {
                while (in.read(buffer) != -1) {
                    // Digest is updated as a side effect of reading.
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("Could not verify build cache blob because SHA-256 is unavailable.", exception);
        }
    }
}
