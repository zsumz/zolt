package sh.zolt.release.archive;

import sh.zolt.project.ProjectConfig;
import sh.zolt.provenance.BuildProvenance;
import sh.zolt.release.ReleaseTarget;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

final class ReleaseArchiveProvenanceJson {
    String write(ProjectConfig config, ReleaseTarget target, BuildProvenance provenance) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schema", "zolt.release-provenance.v1", true);
        field(json, 1, "name", config.project().name(), true);
        field(json, 1, "version", config.project().version(), true);
        field(json, 1, "target", target.id(), true);
        json.append("  \"builder\": {\n");
        field(json, 2, "name", "zolt", true);
        field(json, 2, "version", provenance.zoltVersion(), true);
        field(json, 2, "jdkVersion", provenance.jdkVersion(), true);
        field(json, 2, "jdkVendor", provenance.jdkVendor(), true);
        field(json, 2, "builtAt", DateTimeFormatter.ISO_INSTANT.format(provenance.buildTimestamp()), true);
        optionalField(json, 2, "commit", provenance.git().commitSha(), true);
        optionalField(json, 2, "resolutionFingerprint", provenance.resolutionFingerprint(), false);
        json.append("  }\n");
        json.append("}\n");
        return json.toString();
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
}
