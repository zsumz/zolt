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
 * {@code ..} traversal) because a remote cache is an untrusted source of bytes.
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

    record WriteResult(String sha256, int classCount, long sizeBytes) {
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

    /** Extract into {@code targetDirectory}; returns the number of {@code .class} files written. */
    int extract(Path archiveFile, Path targetDirectory) throws IOException {
        Path root = targetDirectory.toAbsolutePath().normalize();
        Files.createDirectories(root);
        int classCount = 0;
        byte[] buffer = new byte[BUFFER_SIZE];
        try (ZipInputStream zip = new ZipInputStream(new BufferedInputStream(Files.newInputStream(archiveFile)))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Path target = resolveSafely(root, entry.getName());
                Files.createDirectories(target.getParent());
                try (OutputStream out = new BufferedOutputStream(Files.newOutputStream(target))) {
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
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
