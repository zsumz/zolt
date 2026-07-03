package sh.zolt.release.verification;

import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.config;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.passingService;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.sha256;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.smokeAwareService;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.writeBinary;
import static sh.zolt.release.verification.ReleaseVerificationServiceTestSupport.writeProjectFiles;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.archive.ReleaseArchiveResult;
import sh.zolt.release.archive.ReleaseArchiveService;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseVerificationFailureTest {
    @TempDir
    private Path projectDir;

    @Test
    void emptyArchiveListFailsBeforeCreatingWorkDirectory() {
        Path workDirectory = projectDir.resolve("verify-empty");

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> passingService().verify(List.of(), workDirectory, "0.1.0"));

        assertEquals("Release verification needs at least one archive. Pass an archive path.", exception.getMessage());
        assertTrue(Files.notExists(workDirectory));
    }

    @Test
    void unsupportedArchiveSuffixFailsWithSupportedTargets() throws IOException {
        Path archive = projectDir.resolve("zolt-0.1.0-plan9-x64.tar.gz");
        Files.writeString(archive, "not inspected because target inference fails first");

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> passingService().verify(List.of(archive), projectDir.resolve("verify-target"), "0.1.0"));

        assertTrue(exception.getMessage().contains("Could not infer release target"));
        assertTrue(exception.getMessage().contains("zolt-0.1.0-plan9-x64.tar.gz"));
        assertTrue(exception.getMessage().contains(ReleaseTarget.supportedTargets()));
    }

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
    void versionMetadataMismatchFailsBeforeSmokeCommands() throws IOException {
        writeProjectFiles(projectDir);
        Path binary = writeBinary(projectDir, "target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.LINUX_X64,
                binary,
                Path.of("dist"));
        List<List<String>> commands = new ArrayList<>();
        ReleaseVerificationService service = new ReleaseVerificationService((command, directory) -> {
            commands.add(command);
            return new ReleaseVerificationService.ProcessResult(0, "0.2.0\n");
        });

        ReleaseVerificationException exception = assertThrows(
                ReleaseVerificationException.class,
                () -> service.verify(List.of(archive.archivePath()), projectDir.resolve("verify-version"), "0.2.0"));

        assertTrue(exception.getMessage().contains("VERSION metadata did not match expected version 0.2.0"));
        assertTrue(exception.getMessage().contains("Found 0.1.0"));
        assertEquals(List.of(), commands);
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
