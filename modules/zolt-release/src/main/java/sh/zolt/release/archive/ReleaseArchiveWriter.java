package sh.zolt.release.archive;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

final class ReleaseArchiveWriter {
    void write(Path archivePath, List<ReleaseArchiveEntry> entries, boolean zip) throws IOException {
        if (zip) {
            writeZip(archivePath, entries);
            return;
        }
        writeTarGz(archivePath, entries);
    }

    int fileCount(List<ReleaseArchiveEntry> entries) {
        return (int) entries.stream().filter(entry -> !entry.directory()).count();
    }

    private static void writeZip(Path archivePath, List<ReleaseArchiveEntry> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (ReleaseArchiveEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                if (!entry.directory()) {
                    entry.writeTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static void writeTarGz(Path archivePath, List<ReleaseArchiveEntry> entries) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archivePath))) {
            for (ReleaseArchiveEntry entry : entries) {
                long size = entry.size();
                output.write(tarHeader(entry.name(), entry.mode(), size, entry.directory()));
                if (!entry.directory()) {
                    entry.writeTo(output);
                    pad(output, size);
                }
            }
            output.write(new byte[1024]);
        }
    }

    private static byte[] tarHeader(String name, int mode, long size, boolean directory) {
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0);
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        header[156] = (byte) (directory ? '5' : '0');
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        long checksum = 0;
        for (byte value : header) {
            checksum += Byte.toUnsignedInt(value);
        }
        writeOctal(header, 148, 8, checksum);
        header[155] = ' ';
        return header;
    }

    private static void writeString(byte[] output, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length > length) {
            throw new ReleaseArchiveException(
                    "Release archive entry name is too long for tar format: " + value);
        }
        System.arraycopy(bytes, 0, output, offset, bytes.length);
    }

    private static void writeOctal(byte[] output, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int start = offset + length - octal.length() - 1;
        for (int index = offset; index < start; index++) {
            output[index] = '0';
        }
        byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, output, start, bytes.length);
        output[offset + length - 1] = 0;
    }

    private static void pad(OutputStream output, long size) throws IOException {
        int padding = (int) (512 - (size % 512));
        if (padding != 512) {
            output.write(new byte[padding]);
        }
    }
}
