package com.zolt.release.archive;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

final class ReleaseArchiveServiceTest extends ReleaseArchiveTestSupport {
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
        assertEquals(4, result.fileCount());
        assertEquals(
                result.sha256() + "  zolt-0.1.0-macos-arm64.tar.gz\n",
                Files.readString(result.checksumPath()));
        assertTrue(Files.readString(result.manifestPath()).contains("\"target\": \"macos-arm64\""));
        assertTrue(Files.readString(result.manifestPath()).contains("\"sha256\": \"" + result.sha256() + "\""));
        assertEquals(List.of(
                "zolt-0.1.0-macos-arm64/",
                "zolt-0.1.0-macos-arm64/bin/",
                "zolt-0.1.0-macos-arm64/bin/zolt",
                "zolt-0.1.0-macos-arm64/VERSION",
                "zolt-0.1.0-macos-arm64/README.md",
                "zolt-0.1.0-macos-arm64/LICENSE"), tarEntries(result.archivePath()));
    }

    @Test
    void stampsOverriddenNightlyVersionAcrossArchiveNameVersionFileAndManifest() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        String nightly = "0.1.0-nightly.20260628.0123456789ab";

        ReleaseArchiveResult result = service.assemble(
                projectDir,
                config().withVersion(nightly),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));

        assertEquals(
                projectDir.resolve("dist/zolt-" + nightly + "-linux-x64.tar.gz"),
                result.archivePath());
        assertEquals("zolt-" + nightly + "-linux-x64", result.rootDirectory());
        assertTrue(tarEntries(result.archivePath()).contains("zolt-" + nightly + "-linux-x64/VERSION"));
        assertEquals(nightly + "\n", versionFileContent(result.archivePath(), "zolt-" + nightly + "-linux-x64"));
        assertTrue(Files.readString(result.manifestPath()).contains("\"version\": \"" + nightly + "\""));
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
        try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(result.archivePath().toFile())) {
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/bin/zolt.exe")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/VERSION")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/README.md")));
            assertTrue(zip.stream().anyMatch(entry -> entry.getName().equals("zolt-0.1.0-windows-x64/LICENSE")));
        }
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

        assertTrue(tarEntries(result.archivePath()).contains("zolt-0.1.0-linux-x64/README.md"));
        assertTrue(tarEntries(result.archivePath()).stream().noneMatch(entry -> entry.endsWith("/LICENSE")));
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
                "zolt-0.1.0-linux-arm64/VERSION",
                "zolt-0.1.0-linux-arm64/README.md",
                "zolt-0.1.0-linux-arm64/LICENSE"), tarEntries(result.archivePath()));
    }
}
