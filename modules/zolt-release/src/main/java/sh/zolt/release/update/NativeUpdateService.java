package sh.zolt.release.update;

import sh.zolt.release.ReleaseTarget;
import sh.zolt.release.archive.ReleaseArchiveUnpacker;
import sh.zolt.release.channel.ReleaseChannelArtifact;
import sh.zolt.release.channel.ReleaseChannelManifest;
import sh.zolt.release.channel.ReleaseChannelManifestValidator;
import java.io.IOException;
import java.net.URI;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

public final class NativeUpdateService {
    private final ReleaseChannelManifestValidator manifestValidator = new ReleaseChannelManifestValidator();

    public NativeUpdateResult update(NativeUpdateRequest request) {
        try {
            NativeInstalledLayout installed = NativeInstalledLayout.detect(request.installRoot(), request.currentExecutable());
            ReleaseChannelUriPolicy.validate(request.channelUri(), true);
            ReleaseChannelManifest manifest = validateManifest(request.channelUri(), downloadText(request.channelUri()));
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
            Path workRoot = ownedDirectory(workDirectory, "native update work directory");
            Path versionsRoot = ownedDirectory(installed.versionsDirectory(), "native versions directory");
            Path binRoot = ownedDirectory(installed.binLink().getParent(), "native bin directory");
            Path archive = resolveUnderRoot(workRoot, artifact.archive(), "native update archive");
            download(URI.create(artifact.archiveUrl()), archive);
            verifyChecksum(archive, expectedChecksum(artifact, workRoot));

            Path extractDirectory = resolveUnderRoot(workRoot, "extract", "native update extraction directory");
            deleteIfExists(workRoot, extractDirectory, "native update extraction directory");
            Files.createDirectories(extractDirectory);
            unpack(archive, extractDirectory, artifact);
            Path candidateRoot = singleDirectory(extractDirectory);
            assertUnderRoot(extractDirectory, candidateRoot, "native update extraction candidate");
            Path candidateBinary = candidateRoot.resolve("bin").resolve(artifact.binaryName()).normalize();
            assertUnderRoot(candidateRoot, candidateBinary, "native update candidate binary");
            if (!Files.isExecutable(candidateBinary)) {
                throw new NativeUpdateException("Downloaded native Zolt archive does not contain executable bin/" + artifact.binaryName() + ".");
            }

            Path installDirectory = resolveUnderRoot(versionsRoot, manifest.version(), "native install directory");
            Path tempInstallDirectory = resolveUnderRoot(versionsRoot, manifest.version() + ".tmp", "native temporary install directory");
            deleteIfExists(versionsRoot, tempInstallDirectory, "native temporary install directory");
            Files.move(candidateRoot, tempInstallDirectory);
            deleteIfExists(versionsRoot, installDirectory, "native install directory");
            Files.move(tempInstallDirectory, installDirectory);
            Path installedBinary = installDirectory.resolve("bin").resolve(artifact.binaryName());
            assertUnderRoot(installDirectory, installedBinary, "native installed binary");
            smokeCandidate(installedBinary, manifest.version());
            Path nextTarget = Path.of("../versions", manifest.version(), "bin", artifact.binaryName());
            assertUnderRoot(versionsRoot, binRoot.resolve(nextTarget), "native update symlink target");
            // binRoot is realpath-resolved (ownedDirectory), so derive the bin symlink in the same canonical
            // space; otherwise the containment guard's non-following comparison mismatches on hosts where the
            // bin directory's ancestor is a symlink (e.g. macOS /var -> /private/var).
            Path canonicalBinLink = binRoot.resolve(installed.binLink().getFileName());
            switchCurrent(binRoot, canonicalBinLink, installed.linkTarget(), nextTarget);

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

    private ReleaseChannelManifest validateManifest(URI channelUri, String json) {
        if (ReleaseChannelUriPolicy.isLocalFile(channelUri)) {
            return manifestValidator.validateLocalManifest(json);
        }
        return manifestValidator.validate(json);
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
        Path checksum = resolveUnderRoot(workDirectory, artifact.archive() + ".sha256", "native update checksum");
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

    private static void unpack(Path archive, Path destination, ReleaseChannelArtifact artifact) throws IOException {
        ReleaseArchiveUnpacker.unpack(archive, destination, artifact.format(), NativeUpdateException::new);
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

    private static void switchCurrent(Path binRoot, Path binLink, Path previousTarget, Path nextTarget) throws IOException {
        Path tempLink = binLink.resolveSibling(binLink.getFileName() + ".update");
        deleteIfExists(binRoot, tempLink, "native update temporary symlink");
        try {
            Files.createSymbolicLink(tempLink, nextTarget);
            try {
                Files.move(tempLink, binLink, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException exception) {
                Files.move(tempLink, binLink, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            deleteIfExists(binRoot, tempLink, "native update temporary symlink");
            restoreLink(binRoot, binLink, previousTarget);
            throw exception;
        }
    }

    private static void restoreLink(Path binRoot, Path binLink, Path previousTarget) throws IOException {
        deleteIfExists(binRoot, binLink, "native current symlink");
        Files.createSymbolicLink(binLink, previousTarget);
    }

    private static Path ownedDirectory(Path path, String label) throws IOException {
        Path directory = path.toAbsolutePath().normalize();
        Files.createDirectories(directory);
        if (Files.isSymbolicLink(directory)) {
            throw new NativeUpdateException(label + " must not be a symbolic link: " + directory + ".");
        }
        return directory.toRealPath().normalize();
    }

    private static Path resolveUnderRoot(Path root, String child, String label) {
        Path path = root.resolve(child).normalize();
        assertUnderRoot(root, path, label);
        return path;
    }

    private static void assertUnderRoot(Path root, Path path, String label) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedPath = path.toAbsolutePath().normalize();
        if (!normalizedPath.startsWith(normalizedRoot)) {
            throw new NativeUpdateException(label + " must stay under " + normalizedRoot + ": " + normalizedPath + ".");
        }
    }

    private static void deleteIfExists(Path root, Path path, String label) throws IOException {
        assertUnderRoot(root, path, label);
        if (!Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        try (var stream = Files.walk(path)) {
            for (Path entry : stream.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(entry);
            }
        }
    }

}
