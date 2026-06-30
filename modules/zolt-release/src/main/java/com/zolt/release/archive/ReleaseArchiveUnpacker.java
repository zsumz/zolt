package com.zolt.release.archive;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class ReleaseArchiveUnpacker {
    private ReleaseArchiveUnpacker() {
    }

    public static void unpack(Path archive, Path destination, String format, Failure failure) throws IOException {
        if (format.equals("zip")) {
            unpackZip(archive, destination, failure);
            return;
        }
        if (format.equals("tar.gz")) {
            unpackTarGz(archive, destination, failure);
            return;
        }
        throw failure.create("Release archive has unsupported format `" + format + "`.");
    }

    private static void unpackZip(Path archive, Path destination, Failure failure) throws IOException {
        try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = zip.getNextEntry();
            while (entry != null) {
                Path output = safeResolve(destination, entry.getName(), failure);
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(zip, output, StandardCopyOption.REPLACE_EXISTING);
                }
                zip.closeEntry();
                entry = zip.getNextEntry();
            }
        }
    }

    private static void unpackTarGz(Path archive, Path destination, Failure failure) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            byte[] header = input.readNBytes(512);
            while (header.length == 512 && !allZero(header)) {
                String name = readNullTerminated(header, 0, 100);
                int mode = (int) parseOctal(header, 100, 8, "mode", name, failure);
                long size = parseOctal(header, 124, 12, "size", name, failure);
                byte type = header[156];
                Path output = safeResolve(destination, name, failure);
                if (type == '5') {
                    Files.createDirectories(output);
                } else if (type == '0' || type == 0) {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = Files.newOutputStream(output)) {
                        copyExactly(input, file, size, failure);
                    }
                    output.toFile().setExecutable((mode & 0100) != 0);
                    skipPadding(input, size, failure);
                } else if (type == '2') {
                    throw failure.create("Release archive contains unsupported symbolic link entry `" + name + "`.");
                } else if (type == '1') {
                    throw failure.create("Release archive contains unsupported hard link entry `" + name + "`.");
                } else {
                    throw failure.create("Release archive contains unsupported tar entry type `" + (char) type + "` for `" + name + "`.");
                }
                header = input.readNBytes(512);
            }
        }
    }

    private static Path safeResolve(Path destination, String entryName, Failure failure) {
        if (entryName.isBlank() || entryName.contains("\\")) {
            throw failure.create("Release archive contains unsafe entry path `" + entryName + "`.");
        }
        Path output = destination.resolve(entryName).normalize();
        if (!output.startsWith(destination.normalize())) {
            throw failure.create("Release archive contains unsafe entry path `" + entryName + "`.");
        }
        return output;
    }

    private static long parseOctal(
            byte[] bytes,
            int offset,
            int length,
            String field,
            String entryName,
            Failure failure) {
        try {
            return Long.parseLong(readNullTerminated(bytes, offset, length).trim(), 8);
        } catch (NumberFormatException exception) {
            throw failure.create("Release archive contains invalid tar " + field + " for `" + entryName + "`.");
        }
    }

    private static void copyExactly(InputStream input, OutputStream output, long size, Failure failure) throws IOException {
        byte[] buffer = new byte[8192];
        long remaining = size;
        while (remaining > 0) {
            int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
            if (read < 0) {
                throw failure.create("Release archive ended before file content was complete.");
            }
            output.write(buffer, 0, read);
            remaining -= read;
        }
    }

    private static void skipPadding(InputStream input, long size, Failure failure) throws IOException {
        long padding = (512 - (size % 512)) % 512;
        while (padding > 0) {
            long skipped = input.skip(padding);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw failure.create("Release archive ended before file padding was complete.");
                }
                skipped = 1;
            }
            padding -= skipped;
        }
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readNullTerminated(byte[] bytes, int offset, int length) {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(bytes, offset, end - offset, StandardCharsets.UTF_8);
    }

    @FunctionalInterface
    public interface Failure {
        RuntimeException create(String message);
    }
}
