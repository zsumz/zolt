package com.zolt.build;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.zip.CRC32;
import java.util.zip.ZipException;

final class PackageArchiveWriter implements AutoCloseable {
    private static final long DETERMINISTIC_ENTRY_TIME = 0L;

    private final OutputStream fileOutput;
    private final JarOutputStream jarOutput;
    private final Set<String> directoryEntries = new LinkedHashSet<>();

    private PackageArchiveWriter(Path archivePath) throws IOException {
        this.fileOutput = Files.newOutputStream(archivePath);
        this.jarOutput = new JarOutputStream(fileOutput);
    }

    static PackageArchiveWriter open(Path archivePath) throws IOException {
        return new PackageArchiveWriter(archivePath);
    }

    static void writeJarFromFiles(Path jarPath, Path root, List<Path> files) throws IOException {
        try (PackageArchiveWriter archive = open(jarPath)) {
            List<Path> sortedFiles = files.stream()
                    .sorted(Comparator.comparing(file -> entryName(root, file)))
                    .toList();
            for (Path file : sortedFiles) {
                archive.writeFile(entryName(root, file), file);
            }
        }
    }

    void writeEntry(String name, byte[] content) throws IOException {
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            jarOutput.putNextEntry(entry);
            jarOutput.write(content);
            jarOutput.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Remove or rename the duplicate resource and try packaging again.",
                    exception);
        }
    }

    void writeFile(String name, Path file) throws IOException {
        writeEntry(name, Files.readAllBytes(file));
    }

    void writeDirectory(String name) throws IOException {
        if (!directoryEntries.add(name)) {
            return;
        }
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            jarOutput.putNextEntry(entry);
            jarOutput.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Check the package layout and try again.",
                    exception);
        }
    }

    void writeParentDirectories(String entryName) throws IOException {
        int slash = entryName.indexOf('/');
        while (slash >= 0) {
            writeDirectory(entryName.substring(0, slash + 1));
            slash = entryName.indexOf('/', slash + 1);
        }
    }

    void writeStoredEntry(String name, byte[] content) throws IOException {
        try {
            JarEntry entry = new JarEntry(name);
            entry.setTime(DETERMINISTIC_ENTRY_TIME);
            entry.setMethod(JarEntry.STORED);
            entry.setSize(content.length);
            entry.setCompressedSize(content.length);
            CRC32 crc = new CRC32();
            crc.update(content);
            entry.setCrc(crc.getValue());
            jarOutput.putNextEntry(entry);
            jarOutput.write(content);
            jarOutput.closeEntry();
        } catch (ZipException exception) {
            throw new PackageException(
                    "Duplicate jar entry `"
                            + name
                            + "`. Remove or rename the duplicate dependency and try packaging again.",
                    exception);
        }
    }

    @Override
    public void close() throws IOException {
        try (fileOutput; jarOutput) {
            // Closing the jar stream writes archive metadata before the file stream closes.
        }
    }

    private static String entryName(Path root, Path file) {
        return root.relativize(file).toString().replace('\\', '/');
    }
}
