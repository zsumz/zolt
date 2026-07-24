package sh.zolt.build.cache;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class BuildCacheArchiveTest {
    private final BuildCacheArchive archive = new BuildCacheArchive();

    @Test
    void roundTripsClassesAndResourcesButExcludesMachineLocalState(@TempDir Path temp) throws IOException {
        Path source = temp.resolve("classes");
        writeFile(source.resolve("com/example/Widget.class"), "widget-bytes");
        writeFile(source.resolve("com/example/Widget$Inner.class"), "inner-bytes");
        writeFile(source.resolve("application.properties"), "key=value");
        writeFile(source.resolve(".zolt-build-main.fingerprint"), "abs=/machine/local/path");
        writeFile(source.resolve(".zolt-build-main.fingerprint.state"), "state");
        writeFile(source.resolve(".zolt-incremental-main.state"), "incremental");

        Path archiveFile = temp.resolve("entry.zbc");
        BuildCacheArchive.WriteResult result = archive.write(source, archiveFile);
        assertEquals(2, result.classCount(), "only .class files count toward the class total");

        Path target = temp.resolve("restored");
        int restored = archive.extract(archiveFile, target, BuildCacheArchive.ExtractLimits.permissive());
        assertEquals(2, restored);
        assertArrayEquals(
                Files.readAllBytes(source.resolve("com/example/Widget.class")),
                Files.readAllBytes(target.resolve("com/example/Widget.class")));
        assertArrayEquals(
                Files.readAllBytes(source.resolve("application.properties")),
                Files.readAllBytes(target.resolve("application.properties")));
        assertTrue(Files.exists(target.resolve("com/example/Widget$Inner.class")));
        assertFalse(Files.exists(target.resolve(".zolt-build-main.fingerprint")), "machine-local state excluded");
        assertFalse(Files.exists(target.resolve(".zolt-build-main.fingerprint.state")));
        assertFalse(Files.exists(target.resolve(".zolt-incremental-main.state")));
    }

    @Test
    void producesByteIdenticalArchiveForSameContent(@TempDir Path temp) throws IOException {
        Path source = temp.resolve("classes");
        writeFile(source.resolve("a/A.class"), "aaa");
        writeFile(source.resolve("b/B.class"), "bbb");

        Path first = temp.resolve("first.zbc");
        Path second = temp.resolve("second.zbc");
        BuildCacheArchive.WriteResult firstResult = archive.write(source, first);
        BuildCacheArchive.WriteResult secondResult = archive.write(source, second);
        assertEquals(firstResult.sha256(), secondResult.sha256());
        assertArrayEquals(Files.readAllBytes(first), Files.readAllBytes(second));
    }

    @Test
    void refusesArchiveEntryThatEscapesTheOutputDirectory(@TempDir Path temp) throws IOException {
        Path malicious = temp.resolve("malicious.zbc");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(malicious))) {
            ZipEntry entry = new ZipEntry("../escaped.class");
            zip.putNextEntry(entry);
            zip.write("evil".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path target = temp.resolve("out");
        assertThrows(
                IOException.class, () -> archive.extract(malicious, target, BuildCacheArchive.ExtractLimits.permissive()));
        assertFalse(Files.exists(temp.resolve("escaped.class")));
    }

    @Test
    void refusesArchiveExceedingEntryCountLimit(@TempDir Path temp) throws IOException {
        Path archiveFile = temp.resolve("many.zbc");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
            for (int i = 0; i < 5; i++) {
                zip.putNextEntry(new ZipEntry("e" + i + ".class"));
                zip.write("x".getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        BuildCacheArchive.ExtractLimits limits = new BuildCacheArchive.ExtractLimits(2, 1024, 1024);
        Path target = temp.resolve("out");
        assertThrows(IOException.class, () -> archive.extract(archiveFile, target, limits));
    }

    @Test
    void refusesArchiveEntryExceedingPerEntryDecompressedLimit(@TempDir Path temp) throws IOException {
        Path archiveFile = temp.resolve("big-entry.zbc");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
            zip.putNextEntry(new ZipEntry("big.bin"));
            zip.write(new byte[4096]);
            zip.closeEntry();
        }
        // The single entry decompresses to 4096 bytes; a 1024-byte per-entry cap aborts it mid-stream even
        // though a deflated entry does not declare its size up front.
        BuildCacheArchive.ExtractLimits limits = new BuildCacheArchive.ExtractLimits(100, 1024, 1_000_000);
        Path target = temp.resolve("out");
        assertThrows(IOException.class, () -> archive.extract(archiveFile, target, limits));
    }

    @Test
    void refusesArchiveExceedingTotalDecompressedLimit(@TempDir Path temp) throws IOException {
        Path archiveFile = temp.resolve("total.zbc");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archiveFile))) {
            for (int i = 0; i < 4; i++) {
                zip.putNextEntry(new ZipEntry("part" + i + ".bin"));
                zip.write(new byte[1024]);
                zip.closeEntry();
            }
        }
        // Each 1024-byte entry is within the per-entry cap, but their sum exceeds the 2048-byte total cap.
        BuildCacheArchive.ExtractLimits limits = new BuildCacheArchive.ExtractLimits(100, 4096, 2048);
        Path target = temp.resolve("out");
        assertThrows(IOException.class, () -> archive.extract(archiveFile, target, limits));
    }

    private static void writeFile(Path path, String content) throws IOException {
        Files.createDirectories(path.getParent());
        try (OutputStream out = Files.newOutputStream(path)) {
            out.write(content.getBytes(StandardCharsets.UTF_8));
        }
    }
}
