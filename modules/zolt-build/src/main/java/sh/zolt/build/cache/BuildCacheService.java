package sh.zolt.build.cache;

import sh.zolt.build.BuildException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
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
    private final boolean enabled;
    private final LocalBuildCache local;
    private final Optional<RemoteBuildCacheClient> remote;
    private final String zoltVersion;
    private final Set<String> warnings = Collections.synchronizedSet(new LinkedHashSet<>());

    private BuildCacheService(
            boolean enabled,
            LocalBuildCache local,
            Optional<RemoteBuildCacheClient> remote,
            String zoltVersion) {
        this.enabled = enabled;
        this.local = local;
        this.remote = remote == null ? Optional.empty() : remote;
        this.zoltVersion = zoltVersion == null ? "" : zoltVersion;
    }

    public static BuildCacheService disabled() {
        return new BuildCacheService(false, null, Optional.empty(), "");
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
                zoltVersion);
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

    private boolean fetchIntoLocal(BuildCacheKey key, RemoteBuildCacheClient client) throws IOException {
        Optional<byte[]> blob = client.get(key.shardedPath(".zbc"));
        Optional<byte[]> metaBytes = client.get(key.shardedPath(".zbc.meta"));
        if (blob.isEmpty() || metaBytes.isEmpty()) {
            return false;
        }
        Optional<BuildCacheEntryMetadata> metadata =
                BuildCacheEntryMetadata.parse(new String(metaBytes.orElseThrow(), StandardCharsets.UTF_8));
        if (metadata.isEmpty() || !sha256(blob.orElseThrow()).equals(metadata.orElseThrow().sha256())) {
            return false;
        }
        Path temp = Files.createTempFile("zolt-build-cache-", ".zbc");
        try {
            Files.write(temp, blob.orElseThrow());
            local.adopt(key, temp, metadata.orElseThrow());
            return true;
        } finally {
            Files.deleteIfExists(temp);
        }
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

    private static String sha256(byte[] bytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new BuildException("Could not verify build cache blob because SHA-256 is unavailable.", exception);
        }
    }
}
