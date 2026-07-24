package sh.zolt.build.cache;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Reads and writes the deterministic archive of a module's compile output directory.
 *
 * <p>Entries are sorted by name and stamped with an epoch-0 timestamp so the archive is byte-for-byte
 * reproducible from the same content, mirroring the publish bundle precedent. The machine-local
 * fingerprint and incremental-state files are excluded: they carry absolute paths and must be
 * regenerated per machine (documented v1 tradeoff), never restored from a shared cache.
 *
 * <p>Extraction validates every entry name against the target directory (no absolute paths, no
 * {@code ..} traversal) and bounds the entry count and the per-entry and total decompressed sizes,
 * because a remote cache is an untrusted source of bytes and the archive SHA cannot distinguish a
 * legitimate archive from a self-consistent zip bomb.
 */
final class BuildCacheArchive {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;
    private static final int BUFFER_SIZE = 8192;

    /**
     * Machine-local metadata written into the output directory that must not travel in the cache. All
     * are root-level single-segment names produced by the fingerprint and incremental-compile layers.
     */
    private static final Set<String> EXCLUDED_ENTRIES = Set.of(
            ".zolt-build-main.fingerprint",
            ".zolt-build-main.fingerprint.state",
            ".zolt-build-test.fingerprint",
            ".zolt-build-test.fingerprint.state",
            ".zolt-incremental-main.state",
            ".zolt-incremental-test.state");

    /** Entry count ceiling; a single module producing more files than this is pathological. */
    private static final long MAX_ENTRIES = 1_000_000L;

    /** Decompressed output may exceed the archive by at most this factor before it is treated as a bomb. */
    private static final long EXPANSION_RATIO = 100L;

    /** Floor for the total decompressed budget so a tiny (but legitimate) archive is never starved. */
    private static final long MIN_TOTAL_DECOMPRESSED_BYTES = 64L * 1024 * 1024;

    /** Absolute ceiling for any single decompressed entry; a lone .class or resource this large is absurd. */
    private static final long MAX_ENTRY_BYTES_CEILING = 256L * 1024 * 1024;

    record WriteResult(String sha256, int classCount, long sizeBytes) {
    }

    /**
     * Hard limits enforced while extracting an untrusted archive: an entry count, a per-entry decompressed
     * size, and a total decompressed size. They bound a malformed or hostile ("zip bomb") archive that the
     * archive SHA alone cannot catch, since the SHA is only a self-consistent hash of the delivered bytes.
     */
    record ExtractLimits(long maxEntries, long maxEntryBytes, long maxTotalDecompressedBytes) {

        /** Derive limits from the on-disk archive size: bound expansion by a ratio, with a floor and ceiling. */
        static ExtractLimits forArchiveSize(long archiveSizeBytes) {
            long total = Math.max(
                    MIN_TOTAL_DECOMPRESSED_BYTES, saturatingMultiply(Math.max(0L, archiveSizeBytes), EXPANSION_RATIO));
            long perEntry = Math.min(total, MAX_ENTRY_BYTES_CEILING);
            return new ExtractLimits(MAX_ENTRIES, perEntry, total);
        }

        /** Limits wide enough not to constrain a legitimate archive; for callers not exercising the bounds. */
        static ExtractLimits permissive() {
            return new ExtractLimits(Long.MAX_VALUE, Long.MAX_VALUE, Long.MAX_VALUE);
        }

        private static long saturatingMultiply(long value, long factor) {
            long result = value * factor;
            if (value != 0 && result / value != factor) {
                return Long.MAX_VALUE;
            }
            return result;
        }
    }

    WriteResult write(Path sourceDirectory, Path archiveFile) throws IOException {
        List<String> entries = collectEntries(sourceDirectory);
        entries.sort(Comparator.naturalOrder());
        int classCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (OutputStream fileOut = new BufferedOutputStream(Files.newOutputStream(archiveFile));
                ZipOutputStream zip = new ZipOutputStream(fileOut)) {
            for (String entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry);
                zipEntry.setTime(DETERMINISTIC_ENTRY_TIME);
                zip.putNextEntry(zipEntry);
                try (InputStream in = Files.newInputStream(sourceDirectory.resolve(entry))) {
                    int read;
                    while ((read = in.read(buffer)) != -1) {
                        zip.write(buffer, 0, read);
                    }
                }
                zip.closeEntry();
                if (entry.endsWith(".class")) {
                    classCount++;
                }
            }
        }
        return new WriteResult(sha256(archiveFile), classCount, Files.size(archiveFile));
    }

    /**
     * Extract into {@code targetDirectory} under {@code limits}, returning the number of {@code .class}
     * files written. Every entry name is checked against the target (no absolute paths, no {@code ..}
     * traversal), and the entry count plus per-entry and total decompressed sizes are bounded so a
     * malformed or hostile archive aborts with an {@link IOException} instead of exhausting the disk. The
     * declared size is rejected up front, and the actual decompressed bytes are counted as they are read,
     * so an entry that under-declares its size is caught mid-stream.
     */
    int extract(Path archiveFile, Path targetDirectory, ExtractLimits limits) throws IOException {
        Path root = targetDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        int classCount = 0;
        long entryCount = 0;
        long totalBytes = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archiveFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                if (++entryCount > limits.maxEntries()) {
                    throw new IOException("Build cache archive exceeds the entry-count limit of " + limits.maxEntries());
                }
                if (entry.getSize() > limits.maxEntryBytes()) {
                    throw new IOException("Build cache archive entry declares a decompressed size over the "
                            + limits.maxEntryBytes() + "-byte limit: " + entry.getName());
                }
                Path target = resolveSafely(root, entry.getName());
                Files.createDirectories(target.getParent());
                long entryBytes = 0;
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        entryBytes += read;
                        totalBytes += read;
                        if (entryBytes > limits.maxEntryBytes()) {
                            throw new IOException("Build cache archive entry exceeds the "
                                    + limits.maxEntryBytes() + "-byte per-entry limit: " + entry.getName());
                        }
                        if (totalBytes > limits.maxTotalDecompressedBytes()) {
                            throw new IOException("Build cache archive exceeds the total decompressed limit of "
                                    + limits.maxTotalDecompressedBytes() + " bytes");
                        }
                        out.write(buffer, 0, read);
                    }
                }
                if (entry.getName().endsWith(".class")) {
                    classCount++;
                }
            }
        }
        return classCount;
    }

    String sha256(Path file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[BUFFER_SIZE];
            try (DigestInputStream in = new DigestInputStream(Files.newInputStream(file), digest)) {
                while (in.read(buffer) != -1) {
                    // Digest is updated as a side effect of reading.
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 is unavailable", exception);
        }
    }

    private static List<String> collectEntries(Path sourceDirectory) throws IOException {
        Path root = sourceDirectory.toAbsolutePath().normalize();
        try (Stream<Path> paths = Files.walk(root)) {
            List<String> entries = new ArrayList<>();
            paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path.toAbsolutePath().normalize()).toString().replace('\\', '/'))
                    .filter(entry -> !EXCLUDED_ENTRIES.contains(entry))
                    .forEach(entries::add);
            return entries;
        }
    }

    private static Path resolveSafely(Path root, String entryName) throws IOException {
        Path resolved = root.resolve(entryName).normalize();
        if (!resolved.startsWith(root)) {
            throw new IOException("Unsafe build cache archive entry escapes the output directory: " + entryName);
        }
        return resolved;
    }
}
