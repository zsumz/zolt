package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.zolt.project.BuildSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectMetadata;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class ReleaseVerificationServiceTest {
    @TempDir
    private Path projectDir;

    @Test
    void verifiesArchiveChecksumVersionAndInitSmoke() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
        ReleaseArchiveResult archive = new ReleaseArchiveService().assemble(
                projectDir,
                config(),
                ReleaseTarget.MACOS_ARM64,
                binary,
                Path.of("dist"));
        List<List<String>> commands = new ArrayList<>();
        ReleaseVerificationService service = new ReleaseVerificationService((command, directory) -> {
            commands.add(command);
            if (command.contains("init")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.createDirectories(cwd.resolve("smoke"));
                    Files.writeString(cwd.resolve("smoke/zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
                return new ReleaseVerificationService.ProcessResult(0, "Created Zolt project at smoke\n");
            }
            return new ReleaseVerificationService.ProcessResult(0, "0.1.0\n");
        });

        ReleaseVerificationResult result = service.verify(
                List.of(archive.archivePath()),
                projectDir.resolve("verify"),
                "0.1.0");

        assertEquals(1, result.verifiedCount());
        ReleaseVerificationResult.VerifiedArchive verified = result.archives().getFirst();
        assertEquals(archive.archivePath(), verified.archivePath());
        assertTrue(Files.exists(verified.unpackDirectory().resolve("zolt-0.1.0-macos-arm64/bin/zolt")));
        assertEquals(2, commands.size());
        assertEquals(List.of(verified.binaryPath().toString(), "--version"), commands.getFirst());
        assertEquals(List.of(
                verified.binaryPath().toString(),
                "init",
                "--cwd",
                verified.unpackDirectory().resolve("smoke-work").toString(),
                "smoke"), commands.get(1));
    }

    @Test
    void missingChecksumFailsWithArchiveName() throws IOException {
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
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
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
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
        writeProjectFiles();
        Path binary = writeBinary("target/native/zolt");
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

    private ReleaseVerificationService passingService() {
        return new ReleaseVerificationService((command, directory) -> {
            if (command.contains("init")) {
                Path cwd = Path.of(command.get(command.indexOf("--cwd") + 1));
                try {
                    Files.createDirectories(cwd.resolve("smoke"));
                    Files.writeString(cwd.resolve("smoke/zolt.toml"), "[project]\n");
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }
            return new ReleaseVerificationService.ProcessResult(0, "0.1.0\n");
        });
    }

    private void writeProjectFiles() throws IOException {
        Files.writeString(projectDir.resolve("README.md"), "# Demo\n");
    }

    private Path writeBinary(String path) throws IOException {
        Path binary = projectDir.resolve(path);
        Files.createDirectories(binary.getParent());
        Files.writeString(binary, "native");
        return binary;
    }

    private static ProjectConfig config() {
        return new ProjectConfig(
                new ProjectMetadata("zolt", "0.1.0", "com.zolt", currentJavaMajorVersion(), Optional.of("com.zolt.Main")),
                Map.of("central", "https://repo.maven.apache.org/maven2"),
                Map.of(),
                Map.of(),
                BuildSettings.defaults(),
                new NativeSettings("zolt", "target/native", List.of("--no-fallback")));
    }

    private static String sha256(Path archivePath) throws IOException {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(archivePath)));
        } catch (NoSuchAlgorithmException exception) {
            throw new AssertionError(exception);
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
