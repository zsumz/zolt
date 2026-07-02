package sh.zolt.release.archive;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Verifies {@link ReleaseArchiveUnpacker} handles POSIX pax extended headers (tar entry type {@code x}),
 * which BSD/macOS {@code tar} emits and which previously aborted the unpacker. The archives are built
 * by hand so the test is independent of the host {@code tar} flavor.
 */
final class ReleaseArchiveUnpackerPaxTest {

    @Test
    void extractsFileNamedByPaxPathHeader(@TempDir Path dir) throws IOException {
        byte[] content = "binary-bytes".getBytes(UTF_8);
        byte[] tar = concat(
                paxBlock("path=zolt-0.1.1-linux-x64/bin/zolt"),
                fileBlock("PaxHeader-placeholder", content),
                new byte[1024]);
        Path archive = gzip(dir.resolve("pax.tar.gz"), tar);
        Path dest = Files.createDirectories(dir.resolve("out"));

        ReleaseArchiveUnpacker.unpack(archive, dest, "tar.gz", IllegalStateException::new);

        Path extracted = dest.resolve("zolt-0.1.1-linux-x64/bin/zolt");
        assertTrue(Files.exists(extracted), "pax path-named file should be extracted");
        assertArrayEquals(content, Files.readAllBytes(extracted));
    }

    @Test
    void skipsGlobalPaxHeaderAndExtractsFollowingFile(@TempDir Path dir) throws IOException {
        byte[] content = "hello".getBytes(UTF_8);
        byte[] tar = concat(
                globalBlock("comment=ignored"),
                fileBlock("zolt-0.1.1-linux-x64/VERSION", content),
                new byte[1024]);
        Path archive = gzip(dir.resolve("global.tar.gz"), tar);
        Path dest = Files.createDirectories(dir.resolve("out"));

        ReleaseArchiveUnpacker.unpack(archive, dest, "tar.gz", IllegalStateException::new);

        assertArrayEquals(content, Files.readAllBytes(dest.resolve("zolt-0.1.1-linux-x64/VERSION")));
    }

    @Test
    void rejectsPaxPathHeaderAttemptingTraversal(@TempDir Path dir) throws IOException {
        byte[] tar = concat(
                paxBlock("path=../escape"),
                fileBlock("placeholder", "x".getBytes(UTF_8)),
                new byte[1024]);
        Path archive = gzip(dir.resolve("evil.tar.gz"), tar);
        Path dest = Files.createDirectories(dir.resolve("out"));

        RuntimeException failure = assertThrows(
                IllegalStateException.class,
                () -> ReleaseArchiveUnpacker.unpack(archive, dest, "tar.gz", IllegalStateException::new));
        assertTrue(failure.getMessage().contains("unsafe entry path"), failure.getMessage());
    }

    private static byte[] paxBlock(String keyValue) {
        return extendedBlock(keyValue, 'x');
    }

    private static byte[] globalBlock(String keyValue) {
        return extendedBlock(keyValue, 'g');
    }

    private static byte[] extendedBlock(String keyValue, char type) {
        int base = 1 + keyValue.length() + 1;
        int total = base;
        while (Integer.toString(total).length() + base != total) {
            total = Integer.toString(total).length() + base;
        }
        byte[] payload = (total + " " + keyValue + "\n").getBytes(UTF_8);
        return concat(tarHeader("PaxHeader", type, payload.length, 0644), pad512(payload));
    }

    private static byte[] fileBlock(String name, byte[] content) {
        return concat(tarHeader(name, '0', content.length, 0755), pad512(content));
    }

    private static byte[] tarHeader(String name, char type, long size, int mode) {
        byte[] header = new byte[512];
        writeString(header, 0, name, 100);
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 124, 12, size);
        header[156] = (byte) type;
        writeString(header, 257, "ustar", 6);
        for (int i = 148; i < 156; i++) {
            header[i] = ' ';
        }
        int sum = 0;
        for (byte b : header) {
            sum += b & 0xff;
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
        return concat(payload, new byte[padding]);
    }

    private static byte[] concat(byte[]... parts) {
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
