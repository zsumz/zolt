package com.zolt.release;

import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class NativeUpdateService {
    private final ReleaseChannelManifestValidator manifestValidator = new ReleaseChannelManifestValidator();

    public NativeUpdateResult update(NativeUpdateRequest request) {
        try {
            NativeInstalledLayout installed = NativeInstalledLayout.detect(request.installRoot(), request.currentExecutable());
            ReleaseChannelManifest manifest = manifestValidator.validate(downloadText(request.channelUri()));
            ReleaseChannelArtifact artifact = manifest.artifactFor(request.target());
            if (installed.version().equals(manifest.version())) {
                return new NativeUpdateResult(
                        manifest.channel(),
                        request.target(),
                        installed.version(),
                        manifest.version(),
                        false,
                        installed.binLink());
            }

            Path workDirectory = request.workDirectory() == null
                    ? Files.createTempDirectory("zolt-update-")
                    : request.workDirectory().toAbsolutePath().normalize();
            Files.createDirectories(workDirectory);
            Path archive = workDirectory.resolve(artifact.archive());
            download(URI.create(artifact.archiveUrl()), archive);
            verifyChecksum(archive, expectedChecksum(artifact, workDirectory));

            Path extractDirectory = workDirectory.resolve("extract");
            deleteIfExists(extractDirectory);
            Files.createDirectories(extractDirectory);
            unpack(archive, extractDirectory, artifact);
            Path candidateRoot = singleDirectory(extractDirectory);
            Path candidateBinary = candidateRoot.resolve("bin").resolve(artifact.binaryName());
            if (!Files.isExecutable(candidateBinary)) {
                throw new NativeUpdateException("Downloaded native Zolt archive does not contain executable bin/" + artifact.binaryName() + ".");
            }

            Path installDirectory = installed.versionsDirectory().resolve(manifest.version());
            Path tempInstallDirectory = installed.versionsDirectory().resolve(manifest.version() + ".tmp");
            deleteIfExists(tempInstallDirectory);
            Files.move(candidateRoot, tempInstallDirectory);
            deleteIfExists(installDirectory);
            Files.move(tempInstallDirectory, installDirectory);
            Path installedBinary = installDirectory.resolve("bin").resolve(artifact.binaryName());
            smokeCandidate(installedBinary, manifest.version());
            switchCurrent(installed.binLink(), installed.linkTarget(), Path.of("../versions", manifest.version(), "bin", artifact.binaryName()));

            return new NativeUpdateResult(
                    manifest.channel(),
                    request.target(),
                    installed.version(),
                    manifest.version(),
                    true,
                    installed.binLink());
        } catch (IOException exception) {
            throw new NativeUpdateException("Could not update native Zolt: " + exception.getMessage(), exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new NativeUpdateException("Could not update native Zolt: update smoke was interrupted.", exception);
        }
    }

    private static String downloadText(URI uri) throws IOException {
        Path temp = Files.createTempFile("zolt-channel-", ".json");
        try {
            download(uri, temp);
            return Files.readString(temp, StandardCharsets.UTF_8);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static void download(URI uri, Path output) throws IOException {
        if ("file".equals(uri.getScheme())) {
            Files.copy(Path.of(uri), output, StandardCopyOption.REPLACE_EXISTING);
            return;
        }
        URLConnection connection = uri.toURL().openConnection();
        connection.setConnectTimeout(10_000);
        connection.setReadTimeout(10_000);
        try (var input = connection.getInputStream()) {
            Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    private static String expectedChecksum(ReleaseChannelArtifact artifact, Path workDirectory) throws IOException {
        Optional<String> inline = artifact.sha256();
        if (inline.isPresent()) {
            return inline.orElseThrow();
        }
        String checksumUrl = artifact.checksumUrl()
                .orElseThrow(() -> new NativeUpdateException("Release channel artifact `" + artifact.target().id() + "` must include checksumUrl or sha256."));
        Path checksum = workDirectory.resolve(artifact.archive() + ".sha256");
        download(URI.create(checksumUrl), checksum);
        return Files.readString(checksum).split("\\s+")[0];
    }

    private static void verifyChecksum(Path archive, String expected) throws IOException {
        String actual = sha256(archive);
        if (!actual.equals(expected)) {
            throw new NativeUpdateException("Checksum mismatch for native Zolt archive. Expected " + expected + " but found " + actual + ".");
        }
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
            throw new NativeUpdateException("Could not verify native Zolt archive because SHA-256 is unavailable.", exception);
        }
    }

    private static void unpack(Path archive, Path destination, ReleaseChannelArtifact artifact) throws IOException, InterruptedException {
        if (artifact.format().equals("zip")) {
            unzip(archive, destination);
            return;
        }
        Process process = new ProcessBuilder("tar", "-xzf", archive.toString(), "-C", destination.toString())
                .redirectErrorStream(true)
                .start();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new NativeUpdateException("Could not unpack native Zolt archive with tar.");
        }
    }

    private static void unzip(Path archive, Path destination) throws IOException {
        try (ZipInputStream input = new ZipInputStream(Files.newInputStream(archive))) {
            ZipEntry entry = input.getNextEntry();
            while (entry != null) {
                Path output = destination.resolve(entry.getName()).normalize();
                if (!output.startsWith(destination)) {
                    throw new NativeUpdateException("Native Zolt archive contains an unsafe zip entry: " + entry.getName() + ".");
                }
                if (entry.isDirectory()) {
                    Files.createDirectories(output);
                } else {
                    Files.createDirectories(output.getParent());
                    Files.copy(input, output, StandardCopyOption.REPLACE_EXISTING);
                }
                entry = input.getNextEntry();
            }
        }
    }

    private static Path singleDirectory(Path directory) throws IOException {
        List<Path> directories;
        try (var stream = Files.list(directory)) {
            directories = stream.filter(Files::isDirectory).toList();
        }
        if (directories.size() != 1) {
            throw new NativeUpdateException("Native Zolt archive must unpack to exactly one top-level directory.");
        }
        return directories.get(0);
    }

    private static void smokeCandidate(Path executable, String expectedVersion) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(executable.toString(), "--version")
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
        int exitCode = process.waitFor();
        if (exitCode != 0 || !output.equals(expectedVersion)) {
            throw new NativeUpdateException("Downloaded native Zolt failed smoke verification. Expected version " + expectedVersion + " but got `" + output + "`.");
        }
    }

    private static void switchCurrent(Path binLink, Path previousTarget, Path nextTarget) throws IOException {
        Path tempLink = binLink.resolveSibling(binLink.getFileName() + ".update");
        Files.deleteIfExists(tempLink);
        try {
            Files.createSymbolicLink(tempLink, nextTarget);
            try {
                Files.move(tempLink, binLink, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempLink, binLink, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            Files.deleteIfExists(tempLink);
            restoreLink(binLink, previousTarget);
            throw exception;
        }
    }

    private static void restoreLink(Path binLink, Path previousTarget) throws IOException {
        Files.deleteIfExists(binLink);
        Files.createSymbolicLink(binLink, previousTarget);
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

}
