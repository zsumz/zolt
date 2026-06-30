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
            String pendingPath = null;
            long pendingSize = -1;
            byte[] header = input.readNBytes(512);
            while (header.length == 512 && !allZero(header)) {
                String rawName = readNullTerminated(header, 0, 100);
                int mode = (int) parseOctal(header, 100, 8, "mode", rawName, failure);
                long size = parseOctal(header, 124, 12, "size", rawName, failure);
                byte type = header[156];
                // POSIX pax extended (`x`) / global (`g`) headers carry metadata for the next entry,
                // not a file of their own. Read and discard the payload; apply `x` path/size overrides.
                if (type == 'x' || type == 'g') {
                    byte[] payload = readPaxPayload(input, size, failure);
                    skipPadding(input, size, failure);
                    if (type == 'x') {
                        PaxOverrides overrides = parsePax(payload, failure);
                        if (overrides.path() != null) {
                            pendingPath = overrides.path();
                        }
                        if (overrides.size() >= 0) {
                            pendingSize = overrides.size();
                        }
                    }
                    header = input.readNBytes(512);
                    continue;
                }
                String name = pendingPath != null ? pendingPath : rawName;
                long contentSize = pendingSize >= 0 ? pendingSize : size;
                pendingPath = null;
                pendingSize = -1;
                Path output = safeResolve(destination, name, failure);
                if (type == '5') {
                    Files.createDirectories(output);
                } else if (type == '0' || type == 0) {
                    Files.createDirectories(output.getParent());
                    try (OutputStream file = Files.newOutputStream(output)) {
                        copyExactly(input, file, contentSize, failure);
                    }
                    output.toFile().setExecutable((mode & 0100) != 0);
                    skipPadding(input, contentSize, failure);
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

    private static byte[] readPaxPayload(InputStream input, long size, Failure failure) throws IOException {
        if (size < 0 || size > (1 << 20)) {
            throw failure.create("Release archive contains an oversized pax extended header (" + size + " bytes).");
        }
        byte[] payload = input.readNBytes((int) size);
        if (payload.length != size) {
            throw failure.create("Release archive ended inside a pax extended header.");
        }
        return payload;
    }

    // Each pax record is `"<length> key=value\n"` where <length> is the byte length of the whole record.
    private static PaxOverrides parsePax(byte[] payload, Failure failure) {
        String path = null;
        long size = -1;
        int position = 0;
        while (position < payload.length) {
            int space = position;
            while (space < payload.length && payload[space] != ' ') {
                space++;
            }
            int recordLength;
            try {
                recordLength = Integer.parseInt(new String(payload, position, space - position, StandardCharsets.UTF_8));
            } catch (NumberFormatException exception) {
                throw failure.create("Release archive contains a malformed pax extended header record.");
            }
            int keyValueLength = recordLength - (space - position) - 2;
            if (space >= payload.length || recordLength <= 0 || position + recordLength > payload.length
                    || keyValueLength < 0 || payload[position + recordLength - 1] != '\n') {
                throw failure.create("Release archive contains a malformed pax extended header record.");
            }
            String keyValue = new String(payload, space + 1, keyValueLength, StandardCharsets.UTF_8);
            int equals = keyValue.indexOf('=');
            if (equals > 0) {
                String key = keyValue.substring(0, equals);
                String value = keyValue.substring(equals + 1);
                if (key.equals("path")) {
                    path = value;
                } else if (key.equals("size")) {
                    try {
                        size = Long.parseLong(value);
                    } catch (NumberFormatException ignored) {
                        // Fall back to the ustar header size when the pax size record is unparseable.
                    }
                }
            }
            position += recordLength;
        }
        return new PaxOverrides(path, size);
    }

    private record PaxOverrides(String path, long size) {
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
