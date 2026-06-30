package com.zolt.release.archive;

import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectPathException;
import com.zolt.project.ProjectPaths;
import com.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ReleaseArchiveService {
    private final ReleaseArchiveManifestWriter manifestWriter;

    public ReleaseArchiveService() {
        this(new ReleaseArchiveManifestWriter());
    }

    ReleaseArchiveService(ReleaseArchiveManifestWriter manifestWriter) {
        this.manifestWriter = manifestWriter;
    }

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
        String version = ProjectPaths.filenameComponent("[project].version", config.project().version());
        List<ArchiveEntry> entries = entries(projectRoot, binary, rootDirectory, target.binaryName(), version);

        try {
            Files.createDirectories(output);
            if (target.zip()) {
                writeZip(archivePath, entries);
            } else {
                writeTarGz(archivePath, entries);
            }
            String checksum = manifestWriter.checksum(archivePath);
            Path checksumPath = manifestWriter.writeChecksum(archivePath, checksum);
            Path manifestPath = manifestWriter.writeManifest(
                    output,
                    ProjectPaths.filenameComponent("[project].name", config.project().name()),
                    version);
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
        Path configured = Path.of(configuredPath);
        if (configured.isAbsolute()) {
            Path output = configured.normalize();
            if (!output.startsWith(projectRoot) || output.equals(projectRoot)) {
                throw invalidReleaseOutputPath(projectRoot, key, configuredPath, output);
            }
            if (Files.exists(output)) {
                requireExistingReleaseOutputInsideProject(projectRoot, key, configuredPath, output);
            } else {
                requireExistingReleaseOutputAncestorInsideProject(projectRoot, key, configuredPath, output);
            }
            return output;
        }
        try {
            return ProjectPaths.output(projectRoot, key, configuredPath);
        } catch (ProjectPathException exception) {
            throw new ReleaseArchiveException(exception.getMessage(), exception);
        }
    }

    private static void requireExistingReleaseOutputAncestorInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path output) {
        Path ancestor = output.getParent();
        while (ancestor != null && !Files.exists(ancestor)) {
            ancestor = ancestor.getParent();
        }
        if (ancestor != null) {
            requireExistingReleaseOutputInsideProject(projectRoot, key, configuredPath, ancestor);
        }
    }

    private static void requireExistingReleaseOutputInsideProject(
            Path projectRoot,
            String key,
            String configuredPath,
            Path output) {
        try {
            ProjectPaths.requireExistingInsideProject(projectRoot, key, configuredPath, output);
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

    private static ReleaseArchiveException invalidReleaseOutputPath(
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
            String binaryName,
            String version) {
        List<ArchiveEntry> entries = new ArrayList<>();
        entries.add(ArchiveEntry.directory(rootDirectory + "/"));
        entries.add(ArchiveEntry.directory(rootDirectory + "/bin/"));
        entries.add(ArchiveEntry.file(binary, rootDirectory + "/bin/" + binaryName, 0755));
        entries.add(ArchiveEntry.content(
                (version + "\n").getBytes(StandardCharsets.UTF_8),
                rootDirectory + "/VERSION",
                0644));
        addIfPresent(entries, projectDirectory.resolve("README.md"), rootDirectory + "/README.md", 0644);
        addIfPresent(entries, projectDirectory.resolve("LICENSE"), rootDirectory + "/LICENSE", 0644);
        return entries;
    }

    private static void addIfPresent(List<ArchiveEntry> entries, Path source, String name, int mode) {
        if (Files.isRegularFile(source)) {
            entries.add(ArchiveEntry.file(source, name, mode));
        }
    }

    private static void writeZip(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                ZipEntry zipEntry = new ZipEntry(entry.name());
                zipEntry.setTime(0);
                zip.putNextEntry(zipEntry);
                if (!entry.directory()) {
                    entry.writeTo(zip);
                }
                zip.closeEntry();
            }
        }
    }

    private static void writeTarGz(Path archivePath, List<ArchiveEntry> entries) throws IOException {
        try (OutputStream output = new GZIPOutputStream(Files.newOutputStream(archivePath))) {
            for (ArchiveEntry entry : entries) {
                long size = entry.size();
                output.write(tarHeader(entry.name(), entry.mode(), size, entry.directory()));
                if (!entry.directory()) {
                    entry.writeTo(output);
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

    private record ArchiveEntry(Path source, byte[] content, String name, int mode, boolean directory) {
        private static ArchiveEntry file(Path source, String name, int mode) {
            return new ArchiveEntry(source, null, name, mode, false);
        }

        private static ArchiveEntry content(byte[] content, String name, int mode) {
            return new ArchiveEntry(null, content.clone(), name, mode, false);
        }

        private static ArchiveEntry directory(String name) {
            return new ArchiveEntry(null, null, name, 0755, true);
        }

        private long size() throws IOException {
            if (directory) {
                return 0;
            }
            return content == null ? Files.size(source) : content.length;
        }

        private void writeTo(OutputStream output) throws IOException {
            if (content == null) {
                Files.copy(source, output);
            } else {
                output.write(content);
            }
        }
    }

}
