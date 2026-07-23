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
 * {@code <hash>.zbc.meta} sidecar. Restores verify the archive against the sidecar SHA-256 and discard
 * corrupt or partially written entries. Stores write to a temp file and atomically move it into place,
 * so a crashed store never leaves a torn archive a later restore could trust.
 */
public final class LocalBuildCache {
    private static final String ARCHIVE_SUFFIX = ".zbc";
    private static final String META_SUFFIX = ".zbc.meta";
    private static final String OBJECTS_DIR = "objects";

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
     * Restore the module's output into {@code outputDirectory} if a verified entry exists. A corrupt or
     * partial entry is deleted and reported as a miss so the build recompiles and re-stores.
     */
    public BuildCacheRestoreResult restore(BuildCacheKey key, Path outputDirectory) throws IOException {
        Path archivePath = archivePath(key);
        Path metaPath = metaPath(key);
        if (!Files.isRegularFile(archivePath) || !Files.isRegularFile(metaPath)) {
            return BuildCacheRestoreResult.miss();
        }
        Optional<BuildCacheEntryMetadata> metadata = readMetadata(metaPath);
        if (metadata.isEmpty() || !archive.sha256(archivePath).equals(metadata.orElseThrow().sha256())) {
            deleteEntry(key);
            return BuildCacheRestoreResult.miss();
        }
        cleanDirectory(outputDirectory);
        int classCount = archive.extract(archivePath, outputDirectory);
        touch(archivePath);
        return BuildCacheRestoreResult.restoredFrom("local", classCount);
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

    private void cleanDirectory(Path directory) throws IOException {
        if (!Files.exists(directory)) {
            Files.createDirectories(directory);
            return;
        }
        try (Stream<Path> paths = Files.walk(directory)) {
            paths.filter(path -> !path.equals(directory))
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (IOException exception) {
                            throw new UncheckedIOException(exception);
                        }
                    });
        } catch (UncheckedIOException exception) {
            throw exception.getCause();
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
