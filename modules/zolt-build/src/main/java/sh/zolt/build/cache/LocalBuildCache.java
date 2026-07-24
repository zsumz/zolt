package sh.zolt.build.cache;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Content-addressed store for compile output archives under a cache root (default
 * {@code ~/.zolt/build-cache}).
 *
 * <p>Layout: {@code objects/<ab>/<hash>.zbc} (the deterministic archive) with a sibling
 * {@code <hash>.zbc.meta} sidecar. Restores fully verify the entry against the sidecar (requested key,
 * scope, declared size, and archive SHA-256) and discard any corrupt, partial, or mismatched entry
 * before touching output. Extraction is transactional (bounded, staged into a sibling directory, then
 * atomically swapped in) so a failed restore leaves the previous output untouched. Stores write to a
 * temp file and atomically move it into place, so a crashed store never leaves a torn archive a later
 * restore could trust.
 */
public final class LocalBuildCache {
    private static final String ARCHIVE_SUFFIX = ".zbc";
    private static final String META_SUFFIX = ".zbc.meta";
    private static final String OBJECTS_DIR = "objects";
    private static final String STAGING_SUFFIX = ".zolt-restore-";
    private static final String BACKUP_SUFFIX = ".zolt-old-";

    private final Path root;
    private final long maxSizeBytes;
    private final BuildCacheArchive archive = new BuildCacheArchive();

    public LocalBuildCache(Path root, long maxSizeBytes) {
        this.root = root.toAbsolutePath().normalize();
        this.maxSizeBytes = Math.max(0L, maxSizeBytes);
    }

    public Path root() {
        return root;
    }

    /**
     * Restore the module's output into {@code outputDirectory} if a fully verified entry exists. The
     * sidecar must match the requested entry on every axis (key, scope, declared size, and archive
     * SHA-256) before any output is touched; a corrupt, partial, or mismatched entry is deleted and
     * reported as a miss so the build recompiles and re-stores. Extraction is transactional: it runs into
     * a sibling staging directory under bounded limits and is swapped into place atomically only after it
     * completes, so any failure leaves the previous output exactly as it was — never partial.
     */
    public BuildCacheRestoreResult restore(BuildCacheKey key, Path outputDirectory) throws IOException {
        Path archivePath = archivePath(key);
        Path metaPath = metaPath(key);
        if (!Files.isRegularFile(archivePath) || !Files.isRegularFile(metaPath)) {
            return BuildCacheRestoreResult.miss();
        }
        Optional<BuildCacheEntryMetadata> metadata = readMetadata(metaPath);
        if (metadata.isEmpty()) {
            deleteEntryQuietly(key);
            return BuildCacheRestoreResult.miss();
        }
        long actualSize = Files.size(archivePath);
        String actualSha = archive.sha256(archivePath);
        if (metadata.orElseThrow().mismatchAgainst(key, actualSize, actualSha).isPresent()) {
            deleteEntryQuietly(key);
            return BuildCacheRestoreResult.miss();
        }
        try {
            int classCount = restoreTransactionally(archivePath, outputDirectory, actualSize);
            touch(archivePath);
            return BuildCacheRestoreResult.restoredFrom("local", classCount);
        } catch (IOException | RuntimeException exception) {
            // Extraction tripped a limit, hit an unsafe entry, or failed mid-stream. The staged copy was
            // discarded and the live output left untouched; drop the entry so a later build re-stores it.
            deleteEntryQuietly(key);
            return BuildCacheRestoreResult.miss();
        }
    }

    /** Store the module's output; best-effort, so callers treat a failure as a warning, not a build error. */
    public void store(BuildCacheKey key, Path outputDirectory, String zoltVersion) throws IOException {
        if (!Files.isDirectory(outputDirectory)) {
            return;
        }
        Path archivePath = archivePath(key);
        Files.createDirectories(archivePath.getParent());
        Path tempArchive = archivePath.resolveSibling(archivePath.getFileName() + "." + UUID.randomUUID() + ".tmp");
        BuildCacheArchive.WriteResult writeResult;
        try {
            writeResult = archive.write(outputDirectory, tempArchive);
            Files.move(tempArchive, archivePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.deleteIfExists(tempArchive);
            throw exception;
        }
        BuildCacheEntryMetadata metadata = new BuildCacheEntryMetadata(
                key.hash(),
                key.scope().id(),
                zoltVersion,
                Instant.now(),
                writeResult.sha256(),
                writeResult.sizeBytes());
        writeAtomically(metaPath(key), metadata.format());
        pruneIfOverCap();
    }

    /** Populate a local entry from an already-verified archive fetched from a remote cache (Stage 2). */
    public void adopt(BuildCacheKey key, Path verifiedArchive, BuildCacheEntryMetadata metadata) throws IOException {
        Path archivePath = archivePath(key);
        Files.createDirectories(archivePath.getParent());
        Files.copy(verifiedArchive, archivePath, StandardCopyOption.REPLACE_EXISTING);
        writeAtomically(metaPath(key), metadata.format());
        pruneIfOverCap();
    }

    public Optional<Path> archiveFileIfPresent(BuildCacheKey key) {
        Path archivePath = archivePath(key);
        return Files.isRegularFile(archivePath) ? Optional.of(archivePath) : Optional.empty();
    }

    public Optional<Path> metaFileIfPresent(BuildCacheKey key) {
        Path metaPath = metaPath(key);
        return Files.isRegularFile(metaPath) ? Optional.of(metaPath) : Optional.empty();
    }

    public BuildCacheStatus status() {
        List<Entry> entries = entries();
        long total = entries.stream().mapToLong(Entry::size).sum();
        return new BuildCacheStatus(root, entries.size(), total, maxSizeBytes);
    }

    public BuildCachePruneResult prune(long targetMaxBytes) {
        List<Entry> entries = new ArrayList<>(entries());
        long total = entries.stream().mapToLong(Entry::size).sum();
        if (total <= targetMaxBytes) {
            return BuildCachePruneResult.none(total);
        }
        entries.sort(Comparator.comparing(Entry::lastModified));
        int removed = 0;
        long freed = 0L;
        for (Entry entry : entries) {
            if (total <= targetMaxBytes) {
                break;
            }
            long size = entry.size();
            if (deleteEntryFiles(entry.archive())) {
                removed++;
                freed += size;
                total -= size;
            }
        }
        return new BuildCachePruneResult(removed, freed, total);
    }

    private void pruneIfOverCap() {
        if (maxSizeBytes > 0) {
            prune(maxSizeBytes);
        }
    }

    private Path objectsDirectory() {
        return root.resolve(OBJECTS_DIR);
    }

    private Path archivePath(BuildCacheKey key) {
        String hash = key.hash();
        return objectsDirectory().resolve(hash.substring(0, 2)).resolve(hash + ARCHIVE_SUFFIX);
    }

    private Path metaPath(BuildCacheKey key) {
        String hash = key.hash();
        return objectsDirectory().resolve(hash.substring(0, 2)).resolve(hash + META_SUFFIX);
    }

    private Optional<BuildCacheEntryMetadata> readMetadata(Path metaPath) throws IOException {
        return BuildCacheEntryMetadata.parse(Files.readString(metaPath, StandardCharsets.UTF_8));
    }

    private void deleteEntry(BuildCacheKey key) throws IOException {
        Files.deleteIfExists(archivePath(key));
        Files.deleteIfExists(metaPath(key));
    }

    private boolean deleteEntryFiles(Path archivePath) {
        try {
            Files.deleteIfExists(archivePath);
            String name = archivePath.getFileName().toString();
            String hash = name.substring(0, name.length() - ARCHIVE_SUFFIX.length());
            Files.deleteIfExists(archivePath.resolveSibling(hash + META_SUFFIX));
            return true;
        } catch (IOException exception) {
            return false;
        }
    }

    private void touch(Path path) {
        try {
            Files.setLastModifiedTime(path, FileTime.from(Instant.now()));
        } catch (IOException exception) {
            // LRU accuracy is best-effort; a failed touch never affects correctness.
        }
    }

    private List<Entry> entries() {
        Path objects = objectsDirectory();
        if (!Files.isDirectory(objects)) {
            return List.of();
        }
        try (Stream<Path> paths = Files.walk(objects)) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.getFileName().toString().endsWith(ARCHIVE_SUFFIX))
                    .map(Entry::of)
                    .toList();
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /**
     * Extract {@code archivePath} into a fresh sibling of {@code outputDirectory} under limits derived from
     * the archive size, then atomically swap it into place. Because the target is never mutated until a
     * complete, verified extraction is ready, a failure at any point leaves the previous output untouched.
     */
    private int restoreTransactionally(Path archivePath, Path outputDirectory, long archiveSize) throws IOException {
        Path target = outputDirectory.toAbsolutePath().normalize();
        Path parent = target.getParent();
        if (parent == null) {
            throw new IOException("Build cache cannot restore into a filesystem root: " + target);
        }
        Files.createDirectories(parent);
        BuildCacheArchive.ExtractLimits limits = BuildCacheArchive.ExtractLimits.forArchiveSize(archiveSize);
        Path staging = parent.resolve(target.getFileName() + STAGING_SUFFIX + UUID.randomUUID());
        try {
            Files.createDirectories(staging);
            int classCount = archive.extract(archivePath, staging, limits);
            swapIntoPlace(staging, target);
            return classCount;
        } catch (IOException | RuntimeException exception) {
            deleteRecursivelyQuietly(staging);
            throw exception instanceof IOException io ? io : new IOException("Build cache restore failed", exception);
        }
    }

    /**
     * Atomically replace {@code target} with {@code staging} on the same filesystem: move any existing
     * output aside, move the staged output in, then delete the old copy. If the final move fails after the
     * old output was moved aside, it is moved back so the previous state is restored.
     */
    private void swapIntoPlace(Path staging, Path target) throws IOException {
        Path backup = null;
        if (Files.exists(target)) {
            backup = target.resolveSibling(target.getFileName() + BACKUP_SUFFIX + UUID.randomUUID());
            Files.move(target, backup);
        }
        try {
            Files.move(staging, target);
        } catch (IOException exception) {
            if (backup != null) {
                try {
                    Files.move(backup, target);
                } catch (IOException rollback) {
                    exception.addSuppressed(rollback);
                }
            }
            throw exception;
        }
        deleteRecursivelyQuietly(backup);
    }

    private void deleteEntryQuietly(BuildCacheKey key) {
        try {
            deleteEntry(key);
        } catch (IOException exception) {
            // Best effort: leaving a rejected entry costs a re-store, never correctness.
        }
    }

    private static void deleteRecursivelyQuietly(Path root) {
        if (root == null || !Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    // A leaked staging/backup directory is a minor disk cost, never a correctness issue.
                }
            });
        } catch (IOException exception) {
            // Ditto: cleanup is best effort.
        }
    }

    private void writeAtomically(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        Path temp = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    private record Entry(Path archive, long size, Instant lastModified) {
        static Entry of(Path path) {
            try {
                return new Entry(path, Files.size(path), Files.getLastModifiedTime(path).toInstant());
            } catch (IOException exception) {
                return new Entry(path, 0L, Instant.EPOCH);
            }
        }
    }
}
