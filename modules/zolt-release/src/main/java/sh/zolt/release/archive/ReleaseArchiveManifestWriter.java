package sh.zolt.release.archive;

import sh.zolt.provenance.BuildProvenance;
import sh.zolt.release.ReleaseTarget;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

final class ReleaseArchiveManifestWriter {
    String checksum(Path archivePath) throws IOException {
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
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new ReleaseArchiveException(
                    "Could not create SHA-256 checksum. SHA-256 is missing from this JDK.",
                    exception);
        }
    }

    Path writeChecksum(Path archivePath, String checksum) throws IOException {
        Path checksumPath = archivePath.resolveSibling(archivePath.getFileName() + ".sha256");
        Files.writeString(
                checksumPath,
                checksum + "  " + archivePath.getFileName() + "\n",
                StandardCharsets.UTF_8);
        return checksumPath;
    }

    Path writeManifest(Path outputDirectory, String projectName, String version) throws IOException {
        return writeManifest(outputDirectory, projectName, version, Optional.empty());
    }

    Path writeManifest(
            Path outputDirectory,
            String projectName,
            String version,
            Optional<BuildProvenance> provenance) throws IOException {
        List<ManifestEntry> entries = manifestEntries(outputDirectory, projectName, version);
        Path manifestPath = outputDirectory.resolve("release-manifest.json");
        Files.writeString(
                manifestPath,
                manifestJson(projectName, version, entries, provenance),
                StandardCharsets.UTF_8);
        return manifestPath;
    }

    private List<ManifestEntry> manifestEntries(
            Path outputDirectory,
            String projectName,
            String version) throws IOException {
        List<ManifestEntry> entries = new ArrayList<>();
        for (ReleaseTarget target : ReleaseTarget.values()) {
            Path archive = outputDirectory.resolve(projectName + "-" + version + "-" + target.id() + target.archiveExtension());
            if (Files.isRegularFile(archive)) {
                String archiveChecksum = checksum(archive);
                writeChecksum(archive, archiveChecksum);
                entries.add(new ManifestEntry(
                        archive.getFileName().toString(),
                        target.id(),
                        version,
                        target.archiveExtension().substring(1),
                        archiveChecksum));
            }
        }
        entries.sort(Comparator.comparing(ManifestEntry::archive));
        return entries;
    }

    private static String manifestJson(
            String projectName,
            String version,
            List<ManifestEntry> entries,
            Optional<BuildProvenance> provenance) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append("  \"name\": \"").append(json(projectName)).append("\",\n");
        json.append("  \"version\": \"").append(json(version)).append("\",\n");
        provenance.ifPresent(buildProvenance -> builder(json, buildProvenance));
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

    private static void builder(StringBuilder json, BuildProvenance provenance) {
        json.append("  \"builder\": {\n");
        field(json, 2, "name", "zolt", true);
        field(json, 2, "version", provenance.zoltVersion(), true);
        field(json, 2, "jdkVersion", provenance.jdkVersion(), true);
        field(json, 2, "jdkVendor", provenance.jdkVendor(), true);
        field(json, 2, "builtAt", DateTimeFormatter.ISO_INSTANT.format(provenance.buildTimestamp()), true);
        optionalField(json, 2, "commit", provenance.git().commitSha(), true);
        optionalField(json, 2, "resolutionFingerprint", provenance.resolutionFingerprint(), false);
        json.append("  },\n");
    }

    private static void field(
            StringBuilder json,
            int indent,
            String name,
            String value,
            boolean comma) {
        json.append("  ".repeat(indent))
                .append('"')
                .append(json(name))
                .append("\": \"")
                .append(json(value))
                .append('"');
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void optionalField(
            StringBuilder json,
            int indent,
            String name,
            Optional<String> value,
            boolean comma) {
        if (value.isPresent()) {
            field(json, indent, name, value.orElseThrow(), comma);
            return;
        }
        json.append("  ".repeat(indent))
                .append('"')
                .append(json(name))
                .append("\": null");
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ManifestEntry(String archive, String target, String version, String format, String sha256) {
    }
}
