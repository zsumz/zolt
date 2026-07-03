package sh.zolt.release.update;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.channel.ReleaseChannelManifestException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.zip.GZIPOutputStream;
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
    void createsTemporaryWorkDirectoryWhenRequestDoesNotProvideOne() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateResult result = service.update(new NativeUpdateRequest(
                installed.installRoot(),
                installed.binLink(),
                channel.toUri(),
                ReleaseTarget.LINUX_X64,
                null));

        assertTrue(result.updated());
        assertEquals("../versions/0.1.1/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        assertTrue(Files.isExecutable(installed.installRoot().resolve("versions/0.1.1/bin/zolt")));
    }

    @Test
    void skipsDownloadAndWorkDirectoryWhenAlreadyCurrent() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path missingArchive = tempDir.resolve("missing/zolt-0.1.0-linux-x64.tar.gz");
        Path workDirectory = tempDir.resolve("update-work-current");
        Path channel = writeChannel(
                "stable",
                "0.1.0",
                "linux-x64",
                missingArchive,
                missingArchive.getFileName().toString(),
                "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef");

        NativeUpdateResult result = service.update(request(installed, channel, workDirectory));

        assertFalse(result.updated());
        assertEquals("0.1.0", result.previousVersion());
        assertEquals("0.1.0", result.availableVersion());
        assertFalse(Files.exists(workDirectory));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void checksumMismatchFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel(
                "stable",
                "0.1.1",
                "linux-x64",
                archive,
                archive.getFileName().toString(),
                "0000000000000000000000000000000000000000000000000000000000000000");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work-bad-checksum"))));

        assertTrue(exception.getMessage().contains("Checksum mismatch for native Zolt archive"), exception.getMessage());
        assertFalse(Files.exists(installed.installRoot().resolve("versions/0.1.1")));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void smokeVersionMismatchFailsBeforeCurrentSymlinkActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.2.0");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work-smoke-mismatch"))));

        assertTrue(exception.getMessage().contains("Downloaded native Zolt failed smoke verification"), exception.getMessage());
        assertTrue(exception.getMessage().contains("Expected version 0.1.1"), exception.getMessage());
        assertTrue(exception.getMessage().contains("`0.2.0`"), exception.getMessage());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void archiveWithMultipleTopLevelDirectoriesFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = tarArchive(
                "zolt-0.1.1-linux-x64-multiple-roots.tar.gz",
                TarEntry.file("first/bin/zolt", "one"),
                TarEntry.file("second/bin/zolt", "two"));
        Path channel = writeChannel(
                "stable",
                "0.1.1",
                "linux-x64",
                archive,
                archive.getFileName().toString(),
                sha256(archive));

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work-multiple-roots"))));

        assertTrue(exception.getMessage().contains("exactly one top-level directory"), exception.getMessage());
        assertFalse(Files.exists(installed.installRoot().resolve("versions/0.1.1")));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void archiveWithoutExecutableBinaryFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = tarArchive(
                "zolt-0.1.1-linux-x64-no-binary.tar.gz",
                TarEntry.file("zolt-0.1.1-linux-x64/README.txt", "not a binary"));
        Path channel = writeChannel(
                "stable",
                "0.1.1",
                "linux-x64",
                archive,
                archive.getFileName().toString(),
                sha256(archive));

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work-no-binary"))));

        assertTrue(exception.getMessage().contains("does not contain executable bin/zolt"), exception.getMessage());
        assertFalse(Files.exists(installed.installRoot().resolve("versions/0.1.1")));
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void rejectsUnsafeRemoteChannelUrisBeforeManifestDownload() throws IOException {
        InstalledFixture installed = install("0.1.0");
        String[] unsafeChannels = {
            "http://dist.example/channel.json",
            "https://user:pass@dist.example/channel.json",
            "ftp://dist.example/channel.json",
            "jar:https://dist.example/channel.json!/channel.json"
        };

        for (String unsafeChannel : unsafeChannels) {
            NativeUpdateException exception = assertThrows(
                    NativeUpdateException.class,
                    () -> service.update(new NativeUpdateRequest(
                            installed.installRoot(),
                            installed.binLink(),
                            URI.create(unsafeChannel),
                            ReleaseTarget.LINUX_X64,
                            tempDir.resolve("update-work"))));

            assertTrue(exception.getMessage().contains("Release channel URL"), exception.getMessage());
            assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        }
    }

    @Test
    void wrapsAcceptedRemoteChannelDownloadFailureWithoutChangingCurrentInstall() throws IOException {
        InstalledFixture installed = install("0.1.0");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(new NativeUpdateRequest(
                        installed.installRoot(),
                        installed.binLink(),
                        URI.create("https://127.0.0.1:1/channels/stable.json"),
                        ReleaseTarget.LINUX_X64,
                        tempDir.resolve("update-work-remote-failure"))));

        assertTrue(exception.getMessage().contains("Could not update native Zolt:"), exception.getMessage());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
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
    void rejectsSymlinkedWorkDirectoryBeforeDownloadingArchive() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");
        Path realWork = tempDir.resolve("real-update-work");
        Path linkedWork = tempDir.resolve("linked-update-work");
        Files.createDirectories(realWork);
        Files.createSymbolicLink(linkedWork, realWork);

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, linkedWork)));

        assertTrue(exception.getMessage().contains("native update work directory must not be a symbolic link"), exception.getMessage());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void wrapsWorkDirectoryCreationFailureWithUpdateDiagnostic() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");
        Path workDirectory = tempDir.resolve("update-work-file");
        Files.writeString(workDirectory, "not a directory");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, workDirectory)));

        assertTrue(exception.getMessage().contains("Could not update native Zolt:"), exception.getMessage());
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

    @Test
    void tarTraversalEntryFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        String[] unsafeEntries = {
            "../zolt",
            "/tmp/zolt",
            "zolt-0.1.1-linux-x64/../../evil"
        };
        for (int index = 0; index < unsafeEntries.length; index++) {
            Path archive = tarArchive(
                    "zolt-0.1.1-linux-x64-" + index + ".tar.gz",
                    TarEntry.file(unsafeEntries[index], "oops"));
            Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");
            Path workDirectory = tempDir.resolve("update-work-" + index);

            NativeUpdateException exception = assertThrows(
                    NativeUpdateException.class,
                    () -> service.update(request(installed, channel, workDirectory)));

            assertTrue(exception.getMessage().contains("unsafe entry path"), exception.getMessage());
            assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
        }
    }

    @Test
    void tarSymlinkEntryFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = tarArchive("zolt-0.1.1-linux-x64.tar.gz", TarEntry.link("zolt-0.1.1-linux-x64/bin/zolt", '2'));
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work"))));

        assertTrue(exception.getMessage().contains("symbolic link entry"), exception.getMessage());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void tarHardLinkEntryFailsBeforeInstallActivation() throws IOException {
        InstalledFixture installed = install("0.1.0");
        Path archive = tarArchive("zolt-0.1.1-linux-x64.tar.gz", TarEntry.link("zolt-0.1.1-linux-x64/bin/zolt", '1'));
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateException exception = assertThrows(
                NativeUpdateException.class,
                () -> service.update(request(installed, channel, tempDir.resolve("update-work"))));

        assertTrue(exception.getMessage().contains("hard link entry"), exception.getMessage());
        assertEquals("../versions/0.1.0/bin/zolt", Files.readSymbolicLink(installed.binLink()).toString());
    }

    @Test
    void updatesWhenInstallRootIsReachedThroughSymlinkedAncestor() throws IOException {
        // Reproduces the macOS /var -> /private/var case on any OS: the install tree is reached through a
        // symlinked ancestor, so the realpath-resolved binRoot differs from the raw bin link prefix.
        Path realHome = tempDir.resolve("real-home");
        Files.createDirectories(realHome);
        Path linkedHome = tempDir.resolve("linked-home");
        Files.createSymbolicLink(linkedHome, realHome);

        Path installRoot = linkedHome.resolve(".zolt");
        writeFakeZolt(installRoot.resolve("versions").resolve("0.1.0").resolve("bin/zolt"), "0.1.0");
        Path bin = installRoot.resolve("bin");
        Files.createDirectories(bin);
        Path binLink = bin.resolve("zolt");
        Files.createSymbolicLink(binLink, Path.of("../versions", "0.1.0", "bin", "zolt"));
        InstalledFixture installed = new InstalledFixture(installRoot, binLink);

        Path archive = archive("0.1.1", "linux-x64", "0.1.1");
        Path channel = writeChannel("stable", "0.1.1", "linux-x64", archive, archive.getFileName().toString(), "sidecar");

        NativeUpdateResult result = service.update(request(installed, channel, tempDir.resolve("update-work-symlink")));

        assertTrue(result.updated());
        assertEquals("0.1.1", result.availableVersion());
        assertEquals("../versions/0.1.1/bin/zolt", Files.readSymbolicLink(binLink).toString());
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

    private Path tarArchive(String archiveName, TarEntry... entries) throws IOException {
        Path archive = tempDir.resolve("archives").resolve(archiveName);
        Files.createDirectories(archive.getParent());
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archive))) {
            for (TarEntry entry : entries) {
                writeTarEntry(output, entry);
            }
            output.write(new byte[1024]);
        }
        Files.writeString(archive.resolveSibling(archive.getFileName() + ".sha256"), sha256(archive) + "  " + archive.getFileName() + "\n");
        return archive;
    }

    private static void writeTarEntry(OutputStream output, TarEntry entry) throws IOException {
        long size = entry.type() == '0' ? entry.content().length : 0;
        output.write(tarHeader(entry.name(), entry.type(), size));
        if (entry.type() == '0') {
            output.write(entry.content());
            pad(output, size);
        }
    }

    private static byte[] tarHeader(String name, char type, long size) {
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeOctal(header, 100, 8, 0644);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0);
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        header[156] = (byte) type;
        if (type == '1' || type == '2') {
            writeString(header, 157, 100, "../../outside");
        }
        writeString(header, 257, 6, "ustar");
        writeString(header, 263, 2, "00");
        long checksum = 0;
        for (byte value : header) {
            checksum += Byte.toUnsignedInt(value);
        }
        writeOctal(header, 148, 8, checksum);
        header[155] = ' ';
        return header;
    }

    private static void writeString(byte[] output, int offset, int length, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        System.arraycopy(bytes, 0, output, offset, Math.min(bytes.length, length));
    }

    private static void writeOctal(byte[] output, int offset, int length, long value) {
        String octal = Long.toOctalString(value);
        int start = offset + length - octal.length() - 1;
        for (int index = offset; index < start; index++) {
            output[index] = '0';
        }
        byte[] bytes = octal.getBytes(StandardCharsets.US_ASCII);
        System.arraycopy(bytes, 0, output, start, bytes.length);
        output[offset + length - 1] = 0;
    }

    private static void pad(OutputStream output, long size) throws IOException {
        int padding = (int) (512 - (size % 512));
        if (padding != 512) {
            output.write(new byte[padding]);
        }
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

    private record TarEntry(String name, char type, byte[] content) {
        private static TarEntry file(String name, String content) {
            return new TarEntry(name, '0', content.getBytes(StandardCharsets.UTF_8));
        }

        private static TarEntry link(String name, char type) {
            return new TarEntry(name, type, new byte[0]);
        }
    }
}
