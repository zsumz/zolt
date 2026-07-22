package sh.zolt.explain.verify;

import static sh.zolt.explain.verify.VerifyJsonFields.indent;
import static sh.zolt.explain.verify.VerifyJsonFields.intField;
import static sh.zolt.explain.verify.VerifyJsonFields.nullableStringField;
import static sh.zolt.explain.verify.VerifyJsonFields.stringField;

import java.util.List;

/**
 * Serializes a {@link VerifyReport} to a stable, pretty-printed JSON document (schema version 1).
 * Field order and element order are fixed, so identical inputs produce byte-identical output.
 */
public final class VerifyReportJsonWriter {

    public String json(VerifyReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        intField(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "command", "explain-verify", true);
        stringField(json, 1, "buildTool", report.buildTool().token(), true);
        stringField(json, 1, "mavenRoot", report.mavenRoot(), true);
        stringField(json, 1, "zoltRoot", report.zoltRoot(), true);
        summary(json, report);
        modules(json, report.modules());
        json.append("}\n");
        return json.toString();
    }

    private static void summary(StringBuilder json, VerifyReport report) {
        VerifySummary summary = report.summary();
        indent(json, 1).append("\"summary\": {\n");
        intField(json, 2, "modules", summary.modules(), true);
        intField(json, 2, "modulesBoth", summary.modulesBoth(), true);
        intField(json, 2, "modulesMavenOnly", summary.modulesMavenOnly(), true);
        intField(json, 2, "modulesZoltOnly", summary.modulesZoltOnly(), true);
        intField(json, 2, "matched", summary.matched(), true);
        intField(json, 2, "versionDrift", summary.versionDrift(), true);
        intField(json, 2, "onlyInMaven", summary.onlyInMaven(), true);
        intField(json, 2, "onlyInZolt", summary.onlyInZolt(), true);
        stringField(json, 2, "result", report.hasDifferences() ? "differences" : "match", false);
        indent(json, 1).append("},\n");
    }

    private static void modules(StringBuilder json, List<ModuleComparison> modules) {
        indent(json, 1).append("\"modules\": [");
        if (modules.isEmpty()) {
            json.append("]\n");
            return;
        }
        json.append('\n');
        for (int index = 0; index < modules.size(); index++) {
            module(json, modules.get(index), index < modules.size() - 1);
        }
        indent(json, 1).append("]\n");
    }

    private static void module(StringBuilder json, ModuleComparison module, boolean more) {
        indent(json, 2).append("{\n");
        stringField(json, 3, "module", module.moduleKey(), true);
        stringField(json, 3, "presence", module.presence().token(), true);
        nullableStringField(json, 3, "mavenDirectory", module.mavenDirectory().orElse(null), true);
        nullableStringField(json, 3, "zoltMember", module.zoltMember().orElse(null), true);
        stringArray(json, 3, "notes", module.notes(), true);
        scopes(json, module.scopes());
        indent(json, 2).append('}');
        json.append(more ? ",\n" : "\n");
    }

    private static void scopes(StringBuilder json, List<ScopeComparison> scopes) {
        indent(json, 3).append("\"scopes\": [\n");
        for (int index = 0; index < scopes.size(); index++) {
            scope(json, scopes.get(index), index < scopes.size() - 1);
        }
        indent(json, 3).append("]\n");
    }

    private static void scope(StringBuilder json, ScopeComparison scope, boolean more) {
        indent(json, 4).append("{\n");
        stringField(json, 5, "scope", scope.scope().token(), true);
        artifactArray(json, 5, "matched", scope.matched(), true);
        driftArray(json, 5, "versionDrift", scope.versionDrift(), true);
        artifactArray(json, 5, "onlyInMaven", scope.onlyInMaven(), true);
        artifactArray(json, 5, "onlyInZolt", scope.onlyInZolt(), false);
        indent(json, 4).append('}');
        json.append(more ? ",\n" : "\n");
    }

    private static void artifactArray(
            StringBuilder json, int level, String name, List<ResolvedArtifact> artifacts, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (artifacts.isEmpty()) {
            json.append(']');
            VerifyJsonFields.finishLine(json, trailingComma);
            return;
        }
        json.append('\n');
        for (int index = 0; index < artifacts.size(); index++) {
            ResolvedArtifact artifact = artifacts.get(index);
            indent(json, level + 1).append("{");
            json.append("\"group\": ");
            VerifyJsonFields.string(json, artifact.groupId());
            json.append(", \"artifact\": ");
            VerifyJsonFields.string(json, artifact.artifactId());
            json.append(", \"classifier\": ");
            VerifyJsonFields.string(json, artifact.classifier());
            json.append(", \"type\": ");
            VerifyJsonFields.string(json, artifact.type());
            json.append(", \"version\": ");
            VerifyJsonFields.string(json, artifact.version());
            json.append('}');
            json.append(index < artifacts.size() - 1 ? ",\n" : "\n");
        }
        indent(json, level).append(']');
        VerifyJsonFields.finishLine(json, trailingComma);
    }

    private static void driftArray(
            StringBuilder json, int level, String name, List<VersionDrift> drifts, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (drifts.isEmpty()) {
            json.append(']');
            VerifyJsonFields.finishLine(json, trailingComma);
            return;
        }
        json.append('\n');
        for (int index = 0; index < drifts.size(); index++) {
            VersionDrift drift = drifts.get(index);
            indent(json, level + 1).append("{");
            json.append("\"group\": ");
            VerifyJsonFields.string(json, drift.groupId());
            json.append(", \"artifact\": ");
            VerifyJsonFields.string(json, drift.artifactId());
            json.append(", \"classifier\": ");
            VerifyJsonFields.string(json, drift.classifier());
            json.append(", \"mavenVersion\": ");
            VerifyJsonFields.string(json, drift.mavenVersion());
            json.append(", \"zoltVersion\": ");
            VerifyJsonFields.string(json, drift.zoltVersion());
            json.append('}');
            json.append(index < drifts.size() - 1 ? ",\n" : "\n");
        }
        indent(json, level).append(']');
        VerifyJsonFields.finishLine(json, trailingComma);
    }

    private static void stringArray(
            StringBuilder json, int level, String name, List<String> values, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            VerifyJsonFields.string(json, values.get(index));
        }
        json.append(']');
        VerifyJsonFields.finishLine(json, trailingComma);
    }
}
