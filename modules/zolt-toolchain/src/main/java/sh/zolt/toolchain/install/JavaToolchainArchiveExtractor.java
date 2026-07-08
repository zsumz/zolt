package sh.zolt.toolchain.install;

import sh.zolt.error.ActionableException;
import sh.zolt.toolchain.catalog.JavaToolchainArchiveFormat;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

final class JavaToolchainArchiveExtractor {
    private static final int TAR_BLOCK_SIZE = 512;

    void extract(
            Path archive,
            JavaToolchainArchiveFormat format,
            Path destination,
            boolean stripTopLevelDirectory) {
        try {
            Files.createDirectories(destination);
            switch (format) {
                case ZIP -> extractZip(archive, destination, stripTopLevelDirectory);
                case TAR_GZ -> extractTarGz(archive, destination, stripTopLevelDirectory);
            }
        } catch (IOException exception) {
            throw new ActionableException(
                    "Could not extract Java toolchain artifact at " + archive + ".",
                    "Remove the partial toolchain directory and retry `zolt toolchain sync`.");
        }
    }

    private static void extractZip(
            Path archive,
            Path destination,
            boolean stripTopLevelDirectory) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = input.getNextEntry();
            while (entry != null) {
                String name = normalizedName(entry.getName(), stripTopLevelDirectory);
                if (!name.isBlank()) {
                    Path target = safeTarget(destination, name);
                    if (entry.isDirectory()) {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        Files.copy(input, target);
                    }
                }
                input.closeEntry();
                entry = input.getNextEntry();
            }
        }
    }

    private static void extractTarGz(
            Path archive,
            Path destination,
            boolean stripTopLevelDirectory) throws IOException {
        try (InputStream input = new GZIPInputStream(Files.newInputStream(archive))) {
            byte[] header = new byte[TAR_BLOCK_SIZE];
            while (readBlock(input, header)) {
                if (isZeroBlock(header)) {
                    return;
                }
                String rawName = tarName(header);
                long size = parseOctal(header, 124, 12);
                int mode = (int) parseOctal(header, 100, 8);
                byte type = header[156];
                String name = normalizedName(rawName, stripTopLevelDirectory);
                if (!name.isBlank() && (type == 0 || type == '0' || type == '5')) {
                    Path target = safeTarget(destination, name);
                    if (type == '5') {
                        Files.createDirectories(target);
                    } else {
                        Files.createDirectories(target.getParent());
                        copyBytes(input, target, size);
                        if ((mode & 0111) != 0) {
                            target.toFile().setExecutable(true, false);
                        }
                    }
                } else {
                    skipFully(input, size);
                }
                skipPadding(input, size);
            }
        }
    }

    private static String normalizedName(String rawName, boolean stripTopLevelDirectory) {
        String name = rawName == null ? "" : rawName.replace('\\', '/');
        while (name.startsWith("/")) {
            name = name.substring(1);
        }
        if (stripTopLevelDirectory) {
            int slash = name.indexOf('/');
            name = slash < 0 ? "" : name.substring(slash + 1);
        }
        return name;
    }

    private static Path safeTarget(Path destination, String name) {
        Path root = destination.toAbsolutePath().normalize();
        Path target = root.resolve(name).normalize();
        if (!target.startsWith(root)) {
            throw new ActionableException(
                    "Java toolchain archive contains an unsafe path `" + name + "`.",
                    "Use a trusted toolchain archive and retry `zolt toolchain sync`.");
        }
        return target;
    }

    private static boolean readBlock(InputStream input, byte[] block) throws IOException {
        Arrays.fill(block, (byte) 0);
        int offset = 0;
        while (offset < block.length) {
            int read = input.read(block, offset, block.length - offset);
            if (read < 0) {
                return offset != 0;
            }
            offset += read;
        }
        return true;
    }

    private static boolean isZeroBlock(byte[] block) {
        for (byte value : block) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String tarName(byte[] header) {
        String name = string(header, 0, 100);
        String prefix = string(header, 345, 155);
        return prefix.isBlank() ? name : prefix + "/" + name;
    }

    private static String string(byte[] block, int offset, int length) {
        int end = offset;
        int max = offset + length;
        while (end < max && block[end] != 0) {
            end++;
        }
        return new String(block, offset, end - offset, java.nio.charset.StandardCharsets.UTF_8).strip();
    }

    private static long parseOctal(byte[] block, int offset, int length) {
        long value = 0;
        int index = offset;
        int max = offset + length;
        while (index < max && (block[index] == 0 || block[index] == ' ')) {
            index++;
        }
        while (index < max && block[index] >= '0' && block[index] <= '7') {
            value = (value * 8) + (block[index] - '0');
            index++;
        }
        return value;
    }

    private static void copyBytes(InputStream input, Path target, long size) throws IOException {
        try (var output = Files.newOutputStream(target)) {
            byte[] buffer = new byte[8192];
            long remaining = size;
            while (remaining > 0) {
                int read = input.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read < 0) {
                    throw new IOException("Unexpected end of tar entry.");
                }
                output.write(buffer, 0, read);
                remaining -= read;
            }
        }
    }

    private static void skipPadding(InputStream input, long size) throws IOException {
        long padding = (TAR_BLOCK_SIZE - (size % TAR_BLOCK_SIZE)) % TAR_BLOCK_SIZE;
        skipFully(input, padding);
    }

    private static void skipFully(InputStream input, long bytes) throws IOException {
        long remaining = bytes;
        while (remaining > 0) {
            long skipped = input.skip(remaining);
            if (skipped <= 0) {
                if (input.read() < 0) {
                    throw new IOException("Unexpected end of archive.");
                }
                skipped = 1;
            }
            remaining -= skipped;
        }
    }
}
