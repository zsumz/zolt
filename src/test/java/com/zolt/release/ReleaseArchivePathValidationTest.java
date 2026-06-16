package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseArchivePathValidationTest {
    @TempDir
    private Path projectDir;

    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void acceptsAbsoluteReleaseBinaryInsideProject() throws IOException {
        writeProjectFiles();
        writeBinary("target/native/zolt");
        Path binary = projectDir.resolve("target/native/zolt").toAbsolutePath().normalize();

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));

        assertEquals(projectDir.resolve("dist/zolt-0.1.0-linux-x64.tar.gz"), result.archivePath());
        assertTrue(tarEntries(result.archivePath()).contains("zolt-0.1.0-linux-x64/bin/zolt"));
    }

    @Test
    void acceptsAbsoluteReleaseOutputInsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path output = projectDir.resolve("target/native-smoke/release").toAbsolutePath().normalize();

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                output);

        assertEquals(output.resolve("zolt-0.1.0-linux-x64.tar.gz"), result.archivePath());
        assertEquals(output.resolve("zolt-0.1.0-linux-x64.tar.gz.sha256"), result.checksumPath());
        assertEquals(output.resolve("release-manifest.json"), result.manifestPath());
        assertTrue(tarEntries(result.archivePath()).contains("zolt-0.1.0-linux-x64/bin/zolt"));
    }

    @Test
    void rejectsReleaseOutputOutsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("../dist")));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("../dist"));
    }

    @Test
    void rejectsAbsoluteReleaseOutputOutsideProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-dist-");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        outside.toAbsolutePath().normalize()));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("outside-dist"));
        assertTrue(exception.getMessage().contains("project-relative path or an absolute path under"));
    }

    @Test
    void rejectsReleaseBinaryOutsideProject() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-binary-", "");
        Files.writeString(outside, "native");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        projectDir.relativize(outside),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--binary"));
        assertTrue(exception.getMessage().contains("outside-binary"));
    }

    @Test
    void rejectsAbsoluteReleaseBinaryOutsideProject() throws IOException {
        Path outside = Files.createTempFile(projectDir.getParent(), "outside-binary-", "");
        Files.writeString(outside, "native");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        outside.toAbsolutePath().normalize(),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--binary"));
        assertTrue(exception.getMessage().contains("outside-binary"));
        assertTrue(exception.getMessage().contains("project-relative path or an absolute path under"));
    }

    @Test
    void rejectsReleaseOutputSymlinkThatEscapesProject() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        Path outside = Files.createTempDirectory(projectDir.getParent(), "outside-dist-");
        createSymlink(projectDir.resolve("dist"), outside);

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("--output"));
        assertTrue(exception.getMessage().contains("resolved through symlinks"));
        assertFalse(Files.exists(outside.resolve("zolt-0.1.0-linux-x64.tar.gz")));
    }

    private void writeProjectFiles() throws IOException {
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Files.writeString(projectDir.resolve("LICENSE"), "license\n");
    }

    private Path writeBinary(String path) throws IOException {
        Path binary = projectDir.resolve(path);
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        return Path.of(path);
    }

    private static ProjectConfig config() {
        return ProjectConfigs.withDirectDependencies(
                new ProjectMetadata(
                        "zolt",
                        "0.1.0",
                        "com.zolt",
                        currentJavaMajorVersion(),
                        Optional.of("com.zolt.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                new NativeSettings("zolt", "target/native", List.of("--no-fallback")));
    }

    private static List<String> tarEntries(Path archivePath) throws IOException {
        byte[] content;
        try (GZIPInputStream input = new GZIPInputStream(Files.newInputStream(archivePath))) {
            content = input.readAllBytes();
        }
        List<String> entries = new ArrayList<>();
        int offset = 0;
        while (offset + 512 <= content.length) {
            byte[] header = java.util.Arrays.copyOfRange(content, offset, offset + 512);
            if (allZero(header)) {
                break;
            }
            String name = readNullTerminated(header, 0, 100);
            long size = Long.parseLong(readNullTerminated(header, 124, 12).trim(), 8);
            entries.add(name);
            offset += 512 + paddedSize(size);
        }
        return entries;
    }

    private static boolean allZero(byte[] bytes) {
        for (byte value : bytes) {
            if (value != 0) {
                return false;
            }
        }
        return true;
    }

    private static String readNullTerminated(byte[] bytes, int offset, int length) throws IOException {
        int end = offset;
        while (end < offset + length && bytes[end] != 0) {
            end++;
        }
        return new String(new ByteArrayInputStream(bytes, offset, end - offset).readAllBytes(), StandardCharsets.UTF_8);
    }

    private static int paddedSize(long size) {
        long remainder = size % 512;
        return (int) (remainder == 0 ? size : size + 512 - remainder);
    }

    private static void createSymlink(Path link, Path target) throws IOException {
        try {
            Files.createSymbolicLink(link, target);
        } catch (UnsupportedOperationException | IOException exception) {
            Assumptions.assumeTrue(false, "symbolic links are unavailable: " + exception.getMessage());
        }
    }

    private static String currentJavaMajorVersion() {
        String version = System.getProperty("java.version");
        String[] parts = version.split("[._+-]", -1);
        if (parts.length >= 2 && "1".equals(parts[0])) {
            return parts[1];
        }
        return parts[0];
    }
}
