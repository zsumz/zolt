package sh.zolt.explain.maven;

import static sh.zolt.explain.maven.MavenExplainJsonFields.comma;
import static sh.zolt.explain.maven.MavenExplainJsonFields.indent;
import static sh.zolt.explain.maven.MavenExplainJsonFields.intField;
import static sh.zolt.explain.maven.MavenExplainJsonFields.path;
import static sh.zolt.explain.maven.MavenExplainJsonFields.stringArrayField;
import static sh.zolt.explain.maven.MavenExplainJsonFields.stringField;

import sh.zolt.explain.ExplainSignal;
import java.util.List;

final class MavenExplainJsonWriter {
    private final MavenExplainProjectJsonWriter projectWriter = new MavenExplainProjectJsonWriter();

    String json(MavenInspectionResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        intField(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "source", "maven", true);
        stringField(json, 1, "root", path(result.root()), true);
        summary(json, result);
        comma(json);
        projectWriter.projects(json, result.projects());
        comma(json);
        signals(json, result.signals());
        comma(json);
        migration(json, result.signals());
        json.append("\n}\n");
        return json.toString();
    }

    private static void summary(StringBuilder json, MavenInspectionResult result) {
        indent(json, 1).append("\"summary\": {\n");
        intField(json, 2, "projects", result.projects().size(), true);
        intField(json, 2, "signals", result.signals().size(), true);
        intField(json, 2, "blockers", count(result.signals(), ExplainSignal.Severity.BLOCK), true);
        intField(json, 2, "warnings", count(result.signals(), ExplainSignal.Severity.WARN), true);
        intField(json, 2, "unknown", count(result.signals(), ExplainSignal.Severity.UNKNOWN), true);
        intField(json, 2, "ok", count(result.signals(), ExplainSignal.Severity.OK), false);
        indent(json, 1).append("}");
    }

    private static void migration(StringBuilder json, List<ExplainSignal> signals) {
        indent(json, 1).append("\"migration\": {\n");
        stringField(json, 2, "status", migrationStatus(signals), true);
        stringArrayField(json, 2, "nextSteps", nextStepValues(signals), false);
        indent(json, 1).append("}");
    }

    private static void signals(StringBuilder json, List<ExplainSignal> signals) {
        indent(json, 1).append("\"signals\": [");
        if (!signals.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < signals.size(); index++) {
                ExplainSignal signal = signals.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "severity", signal.severity().name().toLowerCase(), true);
                stringField(json, 3, "category", signal.category().name().toLowerCase().replace('_', '-'), true);
                stringField(json, 3, "project", signal.project(), true);
                stringField(json, 3, "id", signal.id(), true);
                stringField(json, 3, "message", signal.message(), true);
                stringField(json, 3, "nextStep", signal.nextStep(), false);
                indent(json, 2).append("}");
                if (index < signals.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static List<String> nextStepValues(List<ExplainSignal> signals) {
        List<String> steps = signals.stream()
                .filter(signal -> signal.severity() == ExplainSignal.Severity.BLOCK
                        || signal.severity() == ExplainSignal.Severity.UNKNOWN)
                .map(ExplainSignal::nextStep)
                .distinct()
                .toList();
        if (steps.isEmpty()) {
            return List.of("Review the static report, then create zolt.toml and run zolt resolve.");
        }
        return steps;
    }

    private static String migrationStatus(List<ExplainSignal> signals) {
        if (signals.stream().anyMatch(signal -> signal.severity() == ExplainSignal.Severity.BLOCK)) {
            return "blocked";
        }
        if (signals.stream().anyMatch(signal -> signal.severity() == ExplainSignal.Severity.UNKNOWN
                || signal.severity() == ExplainSignal.Severity.WARN)) {
            return "manual-review";
        }
        return "ready";
    }

    private static int count(List<ExplainSignal> signals, ExplainSignal.Severity severity) {
        return (int) signals.stream().filter(signal -> signal.severity() == severity).count();
    }
}
