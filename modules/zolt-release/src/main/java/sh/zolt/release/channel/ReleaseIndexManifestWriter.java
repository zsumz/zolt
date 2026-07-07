package sh.zolt.release.channel;

public final class ReleaseIndexManifestWriter {
    public String write(ReleaseIndexManifest manifest) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", Integer.toString(manifest.schemaVersion()), true);
        field(json, 1, "channel", quote(manifest.channel()), true);
        field(json, 1, "updatedAt", quote(manifest.updatedAt()), true);
        json.append("  \"versions\": [\n");
        for (int index = 0; index < manifest.versions().size(); index++) {
            version(json, manifest.versions().get(index), index < manifest.versions().size() - 1);
        }
        json.append("  ]\n");
        json.append("}\n");
        return json.toString();
    }

    private static void version(StringBuilder json, ReleaseIndexVersion version, boolean comma) {
        json.append("    {\n");
        field(json, 3, "version", quote(version.version()), true);
        field(json, 3, "commit", quote(version.commit()), true);
        field(json, 3, "createdAt", quote(version.createdAt()), true);
        json.append("      \"artifacts\": [\n");
        for (int index = 0; index < version.artifacts().size(); index++) {
            artifact(json, version.artifacts().get(index), index < version.artifacts().size() - 1);
        }
        json.append("      ]\n");
        json.append("    }");
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void artifact(StringBuilder json, ReleaseChannelArtifact artifact, boolean comma) {
        json.append("        {\n");
        field(json, 5, "target", quote(artifact.target().id()), true);
        field(json, 5, "archive", quote(artifact.archive()), true);
        field(json, 5, "archiveUrl", quote(artifact.archiveUrl()), true);
        if (artifact.checksumUrl().isPresent()) {
            field(json, 5, "checksumUrl", quote(artifact.checksumUrl().orElseThrow()), true);
        }
        if (artifact.sha256().isPresent()) {
            field(json, 5, "sha256", quote(artifact.sha256().orElseThrow()), true);
        }
        field(json, 5, "format", quote(artifact.format()), true);
        field(json, 5, "binaryName", quote(artifact.binaryName()), artifact.signature().isPresent());
        if (artifact.signature().isPresent()) {
            ReleaseChannelArtifact.Signature signature = artifact.signature().orElseThrow();
            json.append("          \"signature\": {\n");
            field(json, 6, "kind", quote(signature.kind()), true);
            field(json, 6, "url", quote(signature.url()), false);
            json.append("          }\n");
        }
        json.append("        }");
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void field(StringBuilder json, int indent, String name, String value, boolean comma) {
        json.append("  ".repeat(indent))
                .append('"')
                .append(name)
                .append("\": ")
                .append(value);
        if (comma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\b", "\\b")
                .replace("\f", "\\f")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t") + "\"";
    }
}
