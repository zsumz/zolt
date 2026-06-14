package com.zolt.release;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseArchiveService {
    public ReleaseArchiveResult assemble(
            Path projectDirectory,
            ProjectConfig config,
            ReleaseTarget target,
            Path binaryPath,
            Path outputDirectory) {
        Path projectRoot = ProjectPaths.root(projectDirectory);
        Path binary = releaseBinaryInput(projectRoot, "--binary", binaryPath);
        if (!Files.isRegularFile(binary)) {
            throw new ReleaseArchiveException(
                    "Release archive requires native binary at " + binary
                            + ". Run `zolt native` or pass --binary <path>.");
        }

        String releaseBaseName = releaseBaseName(config);
        String rootDirectory = releaseBaseName + "-" + target.id();
        Path output = releaseOutput(projectRoot, "--output", outputDirectory.toString());
        Path archivePath = output.resolve(rootDirectory + target.archiveExtension());
        List<ArchiveEntry> entries = entries(projectRoot, binary, rootDirectory, target.binaryName());

        try {
            Files.createDirectories(output);
            if (target.zip()) {
                writeZip(archivePath, entries);
            } else {
                writeTarGz(archivePath, entries);
            }
            String checksum = sha256(archivePath);
            Path checksumPath = writeChecksum(archivePath, checksum);
            Path manifestPath = writeManifest(
                    output,
                    ProjectPaths.filenameComponent("[project].name", config.project().name()),
                    ProjectPaths.filenameComponent("[project].version", config.project().version()));
            return new ReleaseArchiveResult(
                    target,
                    archivePath,
                    checksumPath,
                    manifestPath,
                    rootDirectory,
                    checksum,
                    fileCount(entries));
        } catch (IOException exception) {
            throw new ReleaseArchiveException(
                    "Could not write release archive " + archivePath + ". Check that the output directory is writable.",
                    exception);
        }
    }

    private static Path releaseBinaryInput(Path projectRoot, String key, Path configuredPath) {
        if (!configuredPath.isAbsolute()) {
            return releaseInput(projectRoot, key, configuredPath.toString());
        }
        Path binary = configuredPath.normalize();
        if (!binary.startsWith(projectRoot)) {
            throw invalidReleaseBinaryPath(projectRoot, key, configuredPath.toString(), binary);
        }
        if (Files.exists(binary)) {
            try {
                ProjectPaths.requireExistingInsideProject(projectRoot, key, configuredPath.toString(), binary);
            } catch (ProjectPathException exception) {
                throw new ReleaseArchiveException(exception.getMessage(), exception);
            }
        }
        return binary;
    }

    private static Path releaseInput(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.input(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static Path releaseOutput(Path projectRoot, String key, String configuredPath) {
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static ReleaseArchiveException invalidReleaseBinaryPath(
            Path projectRoot,
            String key,
            String configuredPath,
            Path resolvedPath) {
        return new ReleaseArchiveException(
                "Invalid "
                        + key
                        + " path `"
                        + configuredPath
                        + "` resolved to "
                        + resolvedPath
                        + ". Use a project-relative path or an absolute path under "
                        + projectRoot
                        + ".");
    }

    private static String releaseBaseName(ProjectConfig config) {
        try {
            return ProjectPaths.filenameComponent("[project].name", config.project().name())
                    + "-"
                    + ProjectPaths.filenameComponent("[project].version", config.project().version());
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static List<ArchiveEntry> entries(
            Path projectDirectory,
            Path binary,
            String rootDirectory,
            String binaryName) {
        List<ArchiveEntry> entries = new ArrayList<>();
        entries.add(ArchiveEntry.directory(rootDirectory + "/"));
        entries.add(ArchiveEntry.directory(rootDirectory + "/bin/"));
        entries.add(ArchiveEntry.file(binary, rootDirectory + "/bin/" + binaryName, 0755));
        addIfPresent(entries, projectDirectory.resolve("README.md"), rootDirectory + "/README.md", 0644);
        addIfPresent(entries, projectDirectory.resolve("LICENSE"), rootDirectory + "/LICENSE", 0644);
        return entries;
    }

    private static void addIfPresent(List<ArchiveEntry> entries, Path source, String name, int mode) {
        if (Files.isRegularFile(source)) {
            entries.add(ArchiveEntry.file(source, name, mode));
        }
    }

    private static String sha256(Path archivePath) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (var input = Files.newInputStream(archivePath)) {
                byte[] buffer = new byte[8192];
                int read = input.read(buffer);
                while (read >= 0) {
                    digest.update(buffer, 0, read);
                    read = input.read(buffer);
                }
            }
            return hex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new ReleaseArchiveException(
                    "Could not create SHA-256 checksum. SHA-256 is missing from this JDK.",
                    exception);
        }
    }

    private static String hex(byte[] bytes) {
        return HexFormat.of().formatHex(bytes);
    }

    private static Path writeChecksum(Path archivePath, String checksum) throws IOException {
        Path checksumPath = archivePath.resolveSibling(archivePath.getFileName() + ".sha256");
        Files.writeString(
                checksumPath,
                checksum + "  " + archivePath.getFileName() + "\n",
                StandardCharsets.UTF_8);
        return checksumPath;
    }

    private static Path writeManifest(Path outputDirectory, String projectName, String version) throws IOException {
        List<ManifestEntry> entries = manifestEntries(outputDirectory, projectName, version);
        Path manifestPath = outputDirectory.resolve("release-manifest.json");
        Files.writeString(manifestPath, manifestJson(projectName, version, entries), StandardCharsets.UTF_8);
        return manifestPath;
    }

    private static List<ManifestEntry> manifestEntries(
            Path outputDirectory,
            String projectName,
            String version) throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        for (ReleaseTarget target : ReleaseTarget.values()) {
            Path archive = outputDirectory.resolve(projectName + "-" + version + "-" + target.id() + target.archiveExtension());
            if (Files.isRegularFile(archive)) {
                String checksum = sha256(archive);
                writeChecksum(archive, checksum);
                entries.add(new ManifestEntry(
                        archive.getFileName().toString(),
                        target.id(),
                        version,
                        target.archiveExtension().substring(1),
                        checksum));
            }
        }
        entries.sort(Comparator.comparing(ManifestEntry::archive));
        return entries;
    }

    private static String manifestJson(String projectName, String version, List<ManifestEntry> entries) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"").append(json(projectName)).append("\",\n");
        json.append("  \"version\": \"").append(json(version)).append("\",\n");
        json.append("  \"archives\": [\n");
        for (int index = 0; index < entries.size(); index++) {
            ManifestEntry entry = entries.get(index);
            json.append("    {\n");
            json.append("      \"archive\": \"").append(json(entry.archive())).append("\",\n");
            json.append("      \"target\": \"").append(json(entry.target())).append("\",\n");
            json.append("      \"version\": \"").append(json(entry.version())).append("\",\n");
            json.append("      \"format\": \"").append(json(entry.format())).append("\",\n");
            json.append("      \"sha256\": \"").append(json(entry.sha256())).append("\"\n");
            json.append("    }");
            if (index + 1 < entries.size()) {
                json.append(",");
            }
            json.append("\n");
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static void writeZip(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                if (!entry.directory()) {
                    Files.copy(entry.source(), zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static void writeTarGz(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                long size = entry.directory() ? 0 : Files.size(entry.source());
                output.write(tarHeader(entry.name(), entry.mode(), size, entry.directory()));
                if (!entry.directory()) {
                    Files.copy(entry.source(), output);
                    pad(output, size);
                }
            }
            output.write(new byte[1024]);
        }
    }

    private static byte[] tarHeader(String name, int mode, long size, boolean directory) {
        byte[] header = new byte[512];
        writeString(header, 0, 100, name);
        writeOctal(header, 100, 8, mode);
        writeOctal(header, 108, 8, 0);
        writeOctal(header, 116, 8, 0);
        writeOctal(header, 124, 12, size);
        writeOctal(header, 136, 12, 0);
        for (int index = 148; index < 156; index++) {
            header[index] = ' ';
        }
        header[156] = (byte) (directory ? '5' : '0');
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
        if (bytes.length > length) {
            throw new ReleaseArchiveException(
                    "Release archive entry name is too long for tar format: " + value);
        }
        System.arraycopy(bytes, 0, output, offset, bytes.length);
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

    private static int fileCount(List<ArchiveEntry> entries) {
        return (int) entries.stream().filter(entry -> !entry.directory()).count();
    }

    private record ArchiveEntry(Path source, String name, int mode, boolean directory) {
        private static ArchiveEntry file(Path source, String name, int mode) {
            return new ArchiveEntry(source, name, mode, false);
        }

        private static ArchiveEntry directory(String name) {
            return new ArchiveEntry(null, name, 0755, true);
        }
    }

    private record ManifestEntry(String archive, String target, String version, String format, String sha256) {
    }
}
