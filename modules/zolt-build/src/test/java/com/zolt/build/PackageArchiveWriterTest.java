package com.zolt.build;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class PackageArchiveWriterTest {
    @TempDir
    private Path tempDir;

    @Test
    void writesDeterministicEntriesDirectoriesAndStoredDependencies() throws IOException {
        Path archivePath = tempDir.resolve("demo.jar");

        try (PackageArchiveWriter archive = PackageArchiveWriter.open(archivePath)) {
            archive.writeDirectory("BOOT-INF/");
            archive.writeDirectory("BOOT-INF/");
            archive.writeParentDirectories("BOOT-INF/classes/com/example/Main.class");
            archive.writeEntry("BOOT-INF/classes/com/example/Main.class", new byte[] {1, 2, 3});
            archive.writeStoredEntry("BOOT-INF/lib/runtime.jar", new byte[] {4, 5, 6});
        }

        try (JarFile jar = new JarFile(archivePath.toFile())) {
            assertNotNull(jar.getEntry("BOOT-INF/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/"));
            assertNotNull(jar.getEntry("BOOT-INF/classes/com/example/"));
            assertEquals(0L, jar.getEntry("BOOT-INF/classes/com/example/Main.class").getTime());
            JarEntry stored = jar.getJarEntry("BOOT-INF/lib/runtime.jar");
            assertEquals(JarEntry.STORED, stored.getMethod());
            assertEquals(0L, stored.getTime());
        }
    }

    @Test
    void writeJarFromFilesUsesRootRelativeSortedEntryNames() throws IOException {
        Path root = tempDir.resolve("classes");
        Path b = root.resolve("com/example/B.class");
        Path a = root.resolve("com/example/A.class");
        Files.createDirectories(a.getParent());
        Files.write(a, new byte[] {1});
        Files.write(b, new byte[] {2});
        Path archivePath = tempDir.resolve("classes.jar");

        PackageArchiveWriter.writeJarFromFiles(archivePath, root, List.of(b, a));

        try (JarFile jar = new JarFile(archivePath.toFile())) {
            assertNotNull(jar.getEntry("com/example/A.class"));
            assertNotNull(jar.getEntry("com/example/B.class"));
            assertEquals(0L, jar.getEntry("com/example/A.class").getTime());
            Enumeration<JarEntry> entries = jar.entries();
            assertEquals("com/example/A.class", entries.nextElement().getName());
            assertEquals("com/example/B.class", entries.nextElement().getName());
        }
    }

    @Test
    void duplicateEntriesFailWithPackageDiagnostic() throws IOException {
        Path archivePath = tempDir.resolve("duplicate.jar");

        PackageException exception = assertThrows(PackageException.class, () -> {
            try (PackageArchiveWriter archive = PackageArchiveWriter.open(archivePath)) {
                archive.writeEntry("com/example/Main.class", new byte[] {1});
                archive.writeEntry("com/example/Main.class", new byte[] {2});
            }
        });

        assertEquals(
                "Duplicate jar entry `com/example/Main.class`. Remove or rename the duplicate resource and try packaging again.",
                exception.getMessage());
    }
}
