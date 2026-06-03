package com.zolt.release;

import com.zolt.project.ProjectConfig;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseArchiveService {
    public ReleaseArchiveResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            ReleaseTarget target,
            Path binaryPath,
            Path outputDirectory) {
        Path binary = projectDirectory.resolve(binaryPath).normalize();
        if (!Files.isRegularFile(binary)) {
            throw new ReleaseArchiveException(
                    "Release archive requires native binary at " + binary
                            + ". Run `zolt native` or pass --binary <path>.");
        }

        String rootDirectory = config.project().name()
                + "-"
                + config.project().version()
                + "-"
                + target.id();
        Path output = projectDirectory.resolve(outputDirectory).normalize();
        Path archivePath = output.resolve(rootDirectory + target.archiveExtension());
        List<ArchiveEntry> entries = entries(projectDirectory, binary, rootDirectory, target.binaryName());

        try {
            Files.createDirectories(output);
            if (target.zip()) {
                writeZip(archivePath, entries);
            } else {
                writeTarGz(archivePath, entries);
            }
            return new ReleaseArchiveResult(target, archivePath, rootDirectory, fileCount(entries));
        } catch (IOException exception) {
            throw new ReleaseArchiveException(
                    "Could not write release archive " + archivePath + ". Check that the output directory is writable.",
                    exception);
        }
    }

    private static List<ArchiveEntry> entries(
            Path projectDirectory,
            Path binary,
            String rootDirectory,
            String binaryName) {
        List<ArchiveEntry> entries = new ArrayList<>();
        entries.add(ArchiveEntry.directory(rootDirectory + "/"));
        entries.add(ArchiveEntry.directory(rootDirectory + "/bin/"));
        entries.add(ArchiveEntry.file(binary, rootDirectory + "/bin/" + binaryName, 0755));
        addIfPresent(entries, projectDirectory.resolve("README.md"), rootDirectory + "/README.md", 0644);
        addIfPresent(entries, projectDirectory.resolve("LICENSE"), rootDirectory + "/LICENSE", 0644);
        return entries;
    }

    private static void addIfPresent(List<ArchiveEntry> entries, Path source, String name, int mode) {
        if (Files.isRegularFile(source)) {
            entries.add(ArchiveEntry.file(source, name, mode));
        }
    }

    private static void writeZip(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                if (!entry.directory()) {
                    Files.copy(entry.source(), zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static void writeTarGz(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                long size = entry.directory() ? 0 : Files.size(entry.source());
                output.write(tarHeader(entry.name(), entry.mode(), size, entry.directory()));
                if (!entry.directory()) {
                    Files.copy(entry.source(), output);
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

    private static int fileCount(List<ArchiveEntry> entries) {
        return (int) entries.stream().filter(entry -> !entry.directory()).count();
    }

    private record ArchiveEntry(Path source, String name, int mode, boolean directory) {
        private static ArchiveEntry file(Path source, String name, int mode) {
            return new ArchiveEntry(source, name, mode, false);
        }

        private static ArchiveEntry directory(String name) {
            return new ArchiveEntry(null, name, 0755, true);
        }
    }
}
