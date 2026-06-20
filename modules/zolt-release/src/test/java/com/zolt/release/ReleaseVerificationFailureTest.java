package com.zolt.release;

import static com.zolt.release.ReleaseVerificationServiceTestSupport.config;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.passingService;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.sha256;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.smokeAwareService;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.writeBinary;
import static com.zolt.release.ReleaseVerificationServiceTestSupport.writeProjectFiles;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseVerificationFailureTest {
    @TempDir
    private Path projectDir;

    @Test
    void missingChecksumFailsWithArchiveName() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));
        Files.delete(archive.checksumPath());

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> passingService().verify(List.of(archive.archivePath()), projectDir.resolve("verify"), "0.1.0"));

        assertTrue(exception.getMessage().contains("Release archive verification failed for " + archive.archivePath()));
        assertTrue(exception.getMessage().contains("missing checksum sidecar"));
    }

    @Test
    void checksumMismatchFailsWithExpectedAndActualChecksums() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));
        Files.writeString(archive.checksumPath(), "0".repeat(64) + "  " + archive.archivePath().getFileName() + "\n");

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> passingService().verify(List.of(archive.archivePath()), projectDir.resolve("verify"), "0.1.0"));

        assertTrue(exception.getMessage().contains("SHA-256 mismatch"));
        assertTrue(exception.getMessage().contains("Expected " + "0".repeat(64)));
        assertTrue(exception.getMessage().contains("but found "));
    }

    @Test
    void versionSmokeFailureReportsArchiveAndOutput() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                binary,
                Path.of("dist"));
        ReleaseVerificationService service = new ReleaseVerificationService((command, directory) ->
                new ReleaseVerificationService.ProcessResult(7, "cannot run\n"));

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> service.verify(List.of(archive.archivePath()), projectDir.resolve("verify"), "0.1.0"));

        assertTrue(exception.getMessage().contains(archive.archivePath().toString()));
        assertTrue(exception.getMessage().contains("`zolt --version` failed with exit code 7"));
        assertTrue(exception.getMessage().contains("cannot run"));
    }

    @Test
    void unsafeZipEntryFailsBeforeSmokeCommands() throws IOException {
        Path archive = projectDir.resolve("zolt-0.1.0-windows-x64.zip");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archive))) {
            zip.putNextEntry(new ZipEntry("../evil.txt"));
            zip.write("oops".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Files.writeString(archive.resolveSibling(archive.getFileName() + ".sha256"),
                sha256(archive) + "  " + archive.getFileName() + "\n");

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> passingService().verify(List.of(archive), projectDir.resolve("verify"), "0.1.0"));

        assertTrue(exception.getMessage().contains("unsafe entry path"));
    }
}
