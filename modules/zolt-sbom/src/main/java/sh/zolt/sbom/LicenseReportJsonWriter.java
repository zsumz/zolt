package sh.zolt.sbom;

import static sh.zolt.sbom.json.JsonWriter.indent;
import static sh.zolt.sbom.json.JsonWriter.optionalStringField;
import static sh.zolt.sbom.json.JsonWriter.rawField;
import static sh.zolt.sbom.json.JsonWriter.string;
import static sh.zolt.sbom.json.JsonWriter.stringField;

import java.util.List;

/** Renders a {@link LicenseReport} as the Zolt-native licenses JSON (schemaVersion 1, groups view). */
public final class LicenseReportJsonWriter {
    public String write(LicenseReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        rawField(json, 1, "schemaVersion", "1", true);
        stringField(json, 1, "command", "licenses", true);
        groups(json, report.groups());
        json.append("\n}\n");
        return json.toString();
    }

    private void groups(StringBuilder json, List<LicenseGroup> groups) {
        indent(json, 1);
        string(json, "groups");
        json.append(": [");
        if (groups.isEmpty()) {
            json.append("]");
            return;
        }
        json.append('\n');
        for (int index = 0; index < groups.size(); index++) {
            group(json, groups.get(index));
            if (index + 1 < groups.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        indent(json, 1).append("]");
    }

    private void group(StringBuilder json, LicenseGroup group) {
        indent(json, 2).append("{\n");
        stringField(json, 3, "license", group.label(), true);
        stringField(json, 3, "status", group.status().jsonValue(), true);
        optionalStringField(json, 3, "url", group.url(), true);
        components(json, group.components());
        json.append('\n');
        indent(json, 2).append("}");
    }

    private void components(StringBuilder json, List<LicenseComponentRef> components) {
        indent(json, 3);
        string(json, "components");
        json.append(": [\n");
        for (int index = 0; index < components.size(); index++) {
            LicenseComponentRef component = components.get(index);
            indent(json, 4).append("{\n");
            stringField(json, 5, "coordinate", component.coordinate(), true);
            stringField(json, 5, "purl", component.purl(), false);
            indent(json, 4).append("}");
            if (index + 1 < components.size()) {
                json.append(',');
            }
            json.append('\n');
        }
        indent(json, 3).append("]");
    }
}
