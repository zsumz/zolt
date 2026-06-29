package com.zolt.release;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

final class NativeUpdateServiceTest {
    @TempDir
    private Path tempDir;

    private final NativeUpdateService service = new NativeUpdateService();

    @Test
    void updatesFromLocalDevelopmentManifestWithFileArtifactUrls() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateResult result = service.update(request(installed, channel, tempDir.resolve("update-work")));

        assertTrue(result.updated());
        assertEquals("0.1.0", result.previousVersion());
        assertEquals("0.1.1", result.availableVersion());
        assertEquals("../versions/0.1.1/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        assertTrue(Files.isExecutable(installed.installRoot().resolve("versions/0.1.1/bin/zolt")));
    }

    @Test
    void unsafeArchiveNameCannotEscapeUpdateWorkDirectory() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path workDirectory = tempDir.resolve("update-work");
        Path escapedArchive = tempDir.resolve("escaped.tar.gz");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, "../escaped.tar.gz", "sidecar");

        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> service.update(request(installed, channel, workDirectory)));

        assertTrue(exception.getMessage().contains("archive"), exception.getMessage());
        assertFalse(Files.exists(escapedArchive));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void unsafeVersionCannotEscapeInstalledVersionsDirectory() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path marker = installed.installRoot().resolve("escape/marker.txt");
        Files.createDirectories(marker.getParent());
        Files.writeString(marker, "keep");
        Path channel = writeChannel("stable", "../escape", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        ReleaseChannelManifestException exception = assertThrows(
                ReleaseChannelManifestException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work"))));

        assertTrue(exception.getMessage().contains("version"), exception.getMessage());
        assertEquals("keep", Files.readString(marker));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void existingTemporaryInstallSymlinkDoesNotDeleteOutsideTarget() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");
        Path outside = tempDir.resolve("outside-temp-target");
        Path marker = outside.resolve("marker.txt");
        Files.createDirectories(outside);
        Files.writeString(marker, "keep");
        Files.createSymbolicLink(installed.installRoot().resolve("versions/0.1.1.tmp"), outside);

        NativeUpdateResult result = service.update(request(installed, channel, tempDir.resolve("update-work")));

        assertTrue(result.updated());
        assertEquals("keep", Files.readString(marker));
        assertEquals("../versions/0.1.1/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    private NativeUpdateRequest request(InstalledFixture installed, Path channel, Path workDirectory) {
        return new NativeUpdateRequest(
                installed.installRoot(),
                installed.binLink(),
                channel.toUri(),
                ReleaseTarget.LINUX_X64,
                workDirectory);
    }

    private InstalledFixture install(String version) throws IOException {
        Path installRoot = tempDir.resolve("home/.zolt");
        Path executable = installRoot.resolve("versions").resolve(version).resolve("bin/zolt");
        writeFakeZolt(executable, version);
        Path bin = installRoot.resolve("bin");
        Files.createDirectories(bin);
        Path binLink = bin.resolve("zolt");
        Files.deleteIfExists(binLink);
        Files.createSymbolicLink(binLink, Path.of("../versions", version, "bin", "zolt"));
        return new InstalledFixture(installRoot, binLink);
    }

    private Path archive(String version, String target, String binaryVersion) throws IOException {
        Path root = tempDir.resolve("archives");
        Path archive = root.resolve("zolt-" + version + "-" + target + ".tar.gz");
        Path packageDir = tempDir.resolve("package/zolt-" + version + "-" + target);
        deleteIfExists(tempDir.resolve("package"));
        writeFakeZolt(packageDir.resolve("bin/zolt"), binaryVersion);
        Files.createDirectories(root);
        try {
            Process process = new ProcessBuilder(
                            "tar",
                            "-C",
                            tempDir.resolve("package").toString(),
                            "-czf",
                            archive.toString(),
                            packageDir.getFileName().toString())
                    .redirectErrorStream(true)
                    .start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new IOException("tar failed: " + new String(process.getInputStream().readAllBytes()));
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IOException("tar interrupted", exception);
        }
        Files.writeString(archive.resolveSibling(archive.getFileName() + ".sha256"), sha256(archive) + "  " + archive.getFileName() + "\n");
        return archive;
    }

    private Path writeChannel(
            String channelName,
            String version,
            String target,
            Path archive,
            String manifestArchive,
            String checksum) throws IOException {
        String checksumField = checksum.equals("sidecar")
                ? "\"checksumUrl\": \"" + archive.resolveSibling(archive.getFileName() + ".sha256").toUri() + "\","
                : "\"sha256\": \"" + checksum + "\",";
        Path channel = tempDir.resolve("channel-" + channelName + "-" + target + ".json");
        Files.writeString(channel, """
                {
                  "schemaVersion": 1,
                  "channel": "%s",
                  "version": "%s",
                  "commit": "0123456789abcdef",
                  "createdAt": "2026-06-28T00:00:00Z",
                  "artifacts": [
                    {
                      "target": "%s",
                      "archive": "%s",
                      "archiveUrl": "%s",
                      %s
                      "format": "tar.gz",
                      "binaryName": "zolt"
                    }
                  ]
                }
                """.formatted(channelName, version, target, manifestArchive, archive.toUri(), checksumField));
        return channel;
    }

    private static void writeFakeZolt(Path executable, String version) throws IOException {
        Files.createDirectories(executable.getParent());
        Files.writeString(executable, """
                #!/usr/bin/env bash
                set -euo pipefail
                if [[ "${1:-}" == "--version" ]]; then
                  printf '%s\\n' "%s"
                  exit 0
                fi
                exit 0
                """.formatted("%s", version));
        executable.toFile().setExecutable(true);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(path)) {
                byte[] buffer = new byte[8192];
                int read = input.read(buffer);
                while (read >= 0) {
                    digest.update(buffer, 0, read);
                    read = input.read(buffer);
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 unavailable", exception);
        }
    }

    private static void deleteIfExists(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

    private record InstalledFixture(Path installRoot, Path binLink) {
    }
}
