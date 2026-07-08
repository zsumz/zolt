package sh.zolt.toolchain.install;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.error.ActionableException;
import sh.zolt.toolchain.catalog.JavaToolchainArchiveFormat;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.GZIPOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class JavaToolchainArchiveExtractorTest {
    @TempDir
    private Path tempDir;

    private final JavaToolchainArchiveExtractor extractor = new JavaToolchainArchiveExtractor();

    @Test
    void extractsTarGzAndStripsTopLevelDirectory() throws IOException {
        Path archive = tempDir.resolve("jdk.tar.gz");
        writeTarGz(
                archive,
                entry("jdk/bin/java", "java", 0755),
                entry("jdk/bin/javac", "javac", 0755),
                entry("jdk/bin/jar", "jar", 0755));
        Path destination = tempDir.resolve("out");

        extractor.extract(archive, JavaToolchainArchiveFormat.TAR_GZ, destination, true);

        assertTrue(Files.isExecutable(destination.resolve("bin/java")));
        assertTrue(Files.readString(destination.resolve("bin/javac")).contains("javac"));
        assertTrue(Files.exists(destination.resolve("bin/jar")));
    }

    @Test
    void rejectsUnsafeTarPaths() throws IOException {
        Path archive = tempDir.resolve("unsafe.tar.gz");
        writeTarGz(archive, entry("jdk/../../escape", "nope", 0644));

        ActionableException exception = assertThrows(ActionableException.class, () ->
                extractor.extract(archive, JavaToolchainArchiveFormat.TAR_GZ, tempDir.resolve("out"), true));

        assertTrue(exception.getMessage().contains("unsafe path"));
    }

    private static TarEntry entry(String name, String content, int mode) {
        return new TarEntry(name, content.getBytes(StandardCharsets.UTF_8), mode);
    }

    private static void writeTarGz(Path archive, TarEntry... entries) throws IOException {
        try (GZIPOutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            for (TarEntry entry : entries) {
                byte[] header = new byte[512];
                putString(header, 0, 100, entry.name());
                putOctal(header, 100, 8, entry.mode());
                putOctal(header, 108, 8, 0);
                putOctal(header, 116, 8, 0);
                putOctal(header, 124, 12, entry.content().length);
                putOctal(header, 136, 12, 0);
                header[156] = '0';
                putString(header, 257, 6, "ustar");
                for (int index = 148; index < 156; index++) {
                    header[index] = ' ';
                }
                long checksum = 0;
                for (byte value : header) {
                    checksum += Byte.toUnsignedInt(value);
                }
                putOctal(header, 148, 8, checksum);
                output.write(header);
                output.write(entry.content());
                int padding = (int) ((512 - (entry.content().length % 512)) % 512);
                output.write(new byte[padding]);
            }
            output.write(new byte[1024]);
        }
    }

    private static void putString(byte[] header, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, header, offset, Math.min(bytes.length, length));
    }

    private static void putOctal(byte[] header, int offset, int length, long value) {
        byte[] bytes = Long.toOctalString(value).getBytes(StandardCharsets.UTF_8);
        int start = offset + length - bytes.length - 1;
        System.arraycopy(bytes, 0, header, start, bytes.length);
        header[offset + length - 1] = 0;
    }

    private record TarEntry(String name, byte[] content, int mode) {
    }
}
