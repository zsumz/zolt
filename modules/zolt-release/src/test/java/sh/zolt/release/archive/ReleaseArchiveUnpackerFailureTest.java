package sh.zolt.release.archive;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseArchiveUnpackerFailureTest {
    @Test
    void rejectsUnsupportedArchiveFormatBeforeReadingArchive(@TempDir Path dir) {
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> ReleaseArchiveUnpacker.unpack(
                        dir.resolve("zolt.bin"),
                        dir.resolve("out"),
                        "jar",
                        IllegalArgumentException::new));

        assertTrue(exception.getMessage().contains("unsupported format `jar`"));
    }

    @Test
    void rejectsTarSymlinkAndHardlinkEntries(@TempDir Path dir) throws IOException {
        Path symlinkArchive = gzip(dir.resolve("symlink.tar.gz"), tar(entry("zolt/bin/zolt", '2', 0, 0777), new byte[1024]));
        Path hardlinkArchive = gzip(dir.resolve("hardlink.tar.gz"), tar(entry("zolt/bin/zolt", '1', 0, 0777), new byte[1024]));

        RuntimeException symlink = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(symlinkArchive, dir.resolve("symlink-out"), "tar.gz", IllegalStateException::new));
        RuntimeException hardlink = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(hardlinkArchive, dir.resolve("hardlink-out"), "tar.gz", IllegalStateException::new));

        assertTrue(symlink.getMessage().contains("unsupported symbolic link entry `zolt/bin/zolt`"));
        assertTrue(hardlink.getMessage().contains("unsupported hard link entry `zolt/bin/zolt`"));
    }

    @Test
    void rejectsInvalidTarModeWithEntryName(@TempDir Path dir) throws IOException {
        byte[] header = entry("zolt/bin/zolt", '0', 0, 0755);
        Arrays.fill(header, 100, 108, (byte) 'x');
        Path archive = gzip(dir.resolve("bad-mode.tar.gz"), tar(header, new byte[1024]));

        RuntimeException exception = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dir.resolve("out"), "tar.gz", IllegalStateException::new));

        assertTrue(exception.getMessage().contains("invalid tar mode for `zolt/bin/zolt`"));
    }

    @Test
    void rejectsMalformedPaxRecords(@TempDir Path dir) throws IOException {
        byte[] payload = "not-a-length path=zolt/bin/zolt\n".getBytes(UTF_8);
        Path archive = gzip(dir.resolve("bad-pax.tar.gz"), tar(
                entry("PaxHeader", 'x', payload.length, 0644),
                pad512(payload),
                new byte[1024]));

        RuntimeException exception = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dir.resolve("out"), "tar.gz", IllegalStateException::new));

        assertTrue(exception.getMessage().contains("malformed pax extended header record"));
    }

    @Test
    void rejectsOversizedAndTruncatedPaxHeaders(@TempDir Path dir) throws IOException {
        Path oversized = gzip(dir.resolve("oversized-pax.tar.gz"), tar(
                entry("PaxHeader", 'x', (1 << 20) + 1, 0644)));
        Path truncated = gzip(dir.resolve("truncated-pax.tar.gz"), tar(
                entry("PaxHeader", 'x', 12, 0644),
                "abc".getBytes(UTF_8)));

        RuntimeException oversizedFailure = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(oversized, dir.resolve("oversized-out"), "tar.gz", IllegalStateException::new));
        RuntimeException truncatedFailure = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(truncated, dir.resolve("truncated-out"), "tar.gz", IllegalStateException::new));

        assertTrue(oversizedFailure.getMessage().contains("oversized pax extended header"), oversizedFailure.getMessage());
        assertTrue(truncatedFailure.getMessage().contains("ended inside a pax extended header"), truncatedFailure.getMessage());
    }

    @Test
    void rejectsUnsupportedTarEntryTypes(@TempDir Path dir) throws IOException {
        Path archive = gzip(dir.resolve("unsupported-type.tar.gz"), tar(
                entry("zolt/socket", '7', 0, 0644),
                new byte[1024]));

        RuntimeException exception = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dir.resolve("out"), "tar.gz", IllegalStateException::new));

        assertTrue(exception.getMessage().contains("unsupported tar entry type `7` for `zolt/socket`"), exception.getMessage());
    }

    @Test
    void rejectsBlankAndBackslashTarEntryPaths(@TempDir Path dir) throws IOException {
        Path blank = gzip(dir.resolve("blank-entry.tar.gz"), tar(entry("", '0', 0, 0644), new byte[1024]));
        Path backslash = gzip(dir.resolve("backslash-entry.tar.gz"), tar(entry("zolt\\bin\\zolt", '0', 0, 0644), new byte[1024]));

        RuntimeException blankFailure = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(blank, dir.resolve("blank-out"), "tar.gz", IllegalStateException::new));
        RuntimeException backslashFailure = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(backslash, dir.resolve("backslash-out"), "tar.gz", IllegalStateException::new));

        assertTrue(blankFailure.getMessage().contains("unsafe entry path ``"), blankFailure.getMessage());
        assertTrue(backslashFailure.getMessage().contains("unsafe entry path `zolt\\bin\\zolt`"), backslashFailure.getMessage());
    }

    @Test
    void rejectsTruncatedTarFileContent(@TempDir Path dir) throws IOException {
        Path archive = gzip(dir.resolve("truncated.tar.gz"), tar(
                entry("zolt/bin/zolt", '0', 12, 0755),
                "short".getBytes(UTF_8)));

        RuntimeException exception = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dir.resolve("out"), "tar.gz", IllegalStateException::new));

        assertTrue(exception.getMessage().contains("ended before file content was complete"));
    }

    @Test
    void rejectsTruncatedTarFilePadding(@TempDir Path dir) throws IOException {
        Path archive = gzip(dir.resolve("truncated-padding.tar.gz"), tar(
                entry("zolt/bin/zolt", '0', 1, 0755),
                "x".getBytes(UTF_8)));

        RuntimeException exception = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dir.resolve("out"), "tar.gz", IllegalStateException::new));

        assertTrue(exception.getMessage().contains("ended before file padding was complete"), exception.getMessage());
    }

    private static byte[] entry(String name, char type, long size, int mode) {
        byte[] header = new byte[512];
        writeString(header, 0, name, 100);
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 124, 12, size);
        header[156] = (byte) type;
        writeString(header, 257, "ustar", 6);
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        int sum = 0;
        for (byte value : header) {
            sum += value & 0xff;
        }
        writeString(header, 148, String.format("%06o", sum), 6);
        header[154] = 0;
        header[155] = ' ';
        return header;
    }

    private static void writeString(byte[] target, int offset, String value, int max) {
        byte[] bytes = value.getBytes(UTF_8);
        System.arraycopy(bytes, 0, target, offset, Math.min(bytes.length, max));
    }

    private static void writeOctal(byte[] target, int offset, int length, long value) {
        StringBuilder digits = new StringBuilder(Long.toOctalString(value));
        while (digits.length() < length - 1) {
            digits.insert(0, '0');
        }
        byte[] bytes = digits.toString().getBytes(UTF_8);
        System.arraycopy(bytes, 0, target, offset, length - 1);
        target[offset + length - 1] = 0;
    }

    private static byte[] pad512(byte[] payload) {
        int padding = (512 - (payload.length % 512)) % 512;
        return tar(payload, new byte[padding]);
    }

    private static byte[] tar(byte[]... parts) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (byte[] part : parts) {
            out.writeBytes(part);
        }
        return out.toByteArray();
    }

    private static Path gzip(Path archive, byte[] tar) throws IOException {
        try (OutputStream out = new GZIPOutputStream(Files.newOutputStream(archive))) {
            out.write(tar);
        }
        return archive;
    }
}
