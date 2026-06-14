package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
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
import java.util.zip.ZipFile;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseArchiveServiceTest {
    @TempDir
    private Path projectDir;

    private final ReleaseArchiveService service = new ReleaseArchiveService();

    @Test
    void assemblesTarGzArchiveForUnixTargets() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                binary,
                Path.of("dist"));

        assertEquals(projectDir.resolve("dist/zolt-0.1.0-macos-arm64.tar.gz"), result.archivePath());
        assertEquals(projectDir.resolve("dist/zolt-0.1.0-macos-arm64.tar.gz.sha256"), result.checksumPath());
        assertEquals(projectDir.resolve("dist/release-manifest.json"), result.manifestPath());
        assertEquals("zolt-0.1.0-macos-arm64", result.rootDirectory());
        assertEquals(64, result.sha256().length());
        assertEquals(3, result.fileCount());
        assertEquals(
                result.sha256() + "  zolt-0.1.0-macos-arm64.tar.gz\n",
                Files.readString(result.checksumPath()));
        assertTrue(Files.readString(result.manifestPath()).contains("\"target\": \"macos-arm64\""));
        assertTrue(Files.readString(result.manifestPath()).contains("\"sha256\": \"" + result.sha256() + "\""));
        assertEquals(List.of(
                "zolt-0.1.0-macos-arm64/",
                "zolt-0.1.0-macos-arm64/bin/",
                "zolt-0.1.0-macos-arm64/bin/zolt",
                "zolt-0.1.0-macos-arm64/README.md",
                "zolt-0.1.0-macos-arm64/LICENSE"), tarEntries(result.archivePath()));
    }

    @Test
    void assemblesZipArchiveForWindowsTarget() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt.exe");

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.WINDOWS_X64,
                binary,
                Path.of("dist"));

        assertEquals(projectDir.resolve("dist/zolt-0.1.0-windows-x64.zip"), result.archivePath());
        assertEquals(projectDir.resolve("dist/zolt-0.1.0-windows-x64.zip.sha256"), result.checksumPath());
        assertEquals(64, result.sha256().length());
        assertTrue(Files.readString(result.manifestPath()).contains("\"format\": \"zip\""));
        try (ZipFile zip = new ZipFile(result.archivePath().toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/bin/zolt.exe")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/README.md")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/LICENSE")));
        }
    }

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
    void skipsLicenseWhenItDoesNotExist() throws IOException {
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));

        List<String> entries = tarEntries(result.archivePath());
        assertTrue(entries.contains("zolt-0.1.0-linux-x64/README.md"));
        assertTrue(entries.stream().noneMatch(entry -> entry.endsWith("/LICENSE")));
    }

    @Test
    void assemblesTarGzArchiveForLinuxArm64Target() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_ARM64,
                binary,
                Path.of("dist"));

        assertEquals(projectDir.resolve("dist/zolt-0.1.0-linux-arm64.tar.gz"), result.archivePath());
        assertEquals("zolt-0.1.0-linux-arm64", result.rootDirectory());
        assertTrue(Files.readString(result.manifestPath()).contains("\"target\": \"linux-arm64\""));
        assertEquals(List.of(
                "zolt-0.1.0-linux-arm64/",
                "zolt-0.1.0-linux-arm64/bin/",
                "zolt-0.1.0-linux-arm64/bin/zolt",
                "zolt-0.1.0-linux-arm64/README.md",
                "zolt-0.1.0-linux-arm64/LICENSE"), tarEntries(result.archivePath()));
    }

    @Test
    void currentTargetInfersLinuxArm64FromAarch64() {
        String originalOs = System.getProperty("os.name");
        String originalArch = System.getProperty("os.arch");
        try {
            System.setProperty("os.name", "Linux");
            System.setProperty("os.arch", "aarch64");

            assertEquals(ReleaseTarget.LINUX_ARM64, ReleaseTarget.current());
        } finally {
            restoreSystemProperty("os.name", originalOs);
            restoreSystemProperty("os.arch", originalArch);
        }
    }

    @Test
    void manifestIsDeterministicAndListsExistingArchives() throws IOException {
        writeProjectFiles();
        Path unixBinary = writeBinary("target/native/zolt");
        Path windowsBinary = writeBinary("target/native/zolt.exe");

        service.assemble(
                projectDir,
                config(),
                ReleaseTarget.WINDOWS_X64,
                windowsBinary,
                Path.of("dist"));
        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                unixBinary,
                Path.of("dist"));

        String manifest = Files.readString(result.manifestPath());
        int macosIndex = manifest.indexOf("\"archive\": \"zolt-0.1.0-macos-arm64.tar.gz\"");
        int windowsIndex = manifest.indexOf("\"archive\": \"zolt-0.1.0-windows-x64.zip\"");
        assertTrue(macosIndex >= 0);
        assertTrue(windowsIndex >= 0);
        assertTrue(macosIndex < windowsIndex);
        assertTrue(Files.exists(projectDir.resolve("dist/zolt-0.1.0-macos-arm64.tar.gz.sha256")));
        assertTrue(Files.exists(projectDir.resolve("dist/zolt-0.1.0-windows-x64.zip.sha256")));

        service.assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                unixBinary,
                Path.of("dist"));
        assertEquals(manifest, Files.readString(result.manifestPath()));
    }

    @Test
    void missingBinaryFailsWithNextStep() {
        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(),
                        ReleaseTarget.LINUX_X64,
                        Path.of("target/native/zolt"),
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("Release archive requires native binary"));
        assertTrue(exception.getMessage().contains("Run `zolt native` or pass --binary <path>"));
    }

    @Test
    void rejectsArchiveNameThatUsesUnsafeProjectVersion() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");

        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> service.assemble(
                        projectDir,
                        config(new ProjectMetadata(
                                "zolt",
                                "../0.1.0",
                                "com.zolt",
                                currentJavaMajorVersion(),
                                Optional.of("com.zolt.Main"))),
                        ReleaseTarget.LINUX_X64,
                        binary,
                        Path.of("dist")));

        assertTrue(exception.getMessage().contains("[project].version"));
        assertTrue(exception.getMessage().contains("../0.1.0"));
        assertFalse(Files.exists(projectDir.resolve("dist/zolt-0.1.0-linux-x64.tar.gz")));
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

    @Test
    void unknownTargetListsSupportedTargets() {
        ReleaseArchiveException exception = assertThrows(
                ReleaseArchiveException.class,
                () -> ReleaseTarget.fromId("solaris-sparc"));

        assertTrue(exception.getMessage().contains("Unknown release target `solaris-sparc`"));
        assertTrue(exception.getMessage().contains("macos-arm64"));
        assertTrue(exception.getMessage().contains("linux-arm64"));
        assertTrue(exception.getMessage().contains("windows-x64"));
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
        return config(new ProjectMetadata(
                "zolt",
                "0.1.0",
                "com.zolt",
                currentJavaMajorVersion(),
                Optional.of("com.zolt.Main")));
    }

    private static ProjectConfig config(ProjectMetadata projectMetadata) {
        return new ProjectConfig(
                projectMetadata,
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

    private static void restoreSystemProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
