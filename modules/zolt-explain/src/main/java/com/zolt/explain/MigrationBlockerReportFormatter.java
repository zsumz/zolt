package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public final class MigrationBlockerReportFormatter {
    public String text(MigrationBlockerReport report) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt migration blocker report: ")
                .append(report.source())
                .append(" project\n\n");
        output.append("Project\n");
        output.append("  Root: ").append(report.root()).append('\n');
        output.append("  Status: ").append(report.status()).append('\n');
        output.append("  Findings: ").append(report.findings().size()).append('\n');

        output.append("\nFindings\n");
        if (report.findings().isEmpty()) {
            output.append("  ok  no migration blockers found in this static inspection pass\n");
        } else {
            for (MigrationReadinessFinding finding : report.findings()) {
                output.append("  ")
                        .append(finding.category().label())
                        .append("  ")
                        .append(MigrationBlockerReports.sourcePattern(finding))
                        .append(" -> ")
                        .append(finding.zoltPrimitive());
                if (!finding.followUp().isBlank()) {
                    output.append(" (").append(finding.followUp()).append(')');
                }
                appendFindingDetail(output, finding);
                output.append('\n')
                        .append("      signal: ")
                        .append(finding.signalId())
                        .append(", severity: ")
                        .append(finding.severity().name().toLowerCase())
                        .append('\n')
                        .append("      next: ")
                        .append(finding.nextStep())
                        .append('\n');
            }
        }

        output.append("\nNext steps\n");
        if (report.nextSteps().isEmpty()) {
            output.append("  1. Create zolt.toml, then run zolt resolve, zolt plan, and zolt check.\n");
        } else {
            for (int index = 0; index < report.nextSteps().size(); index++) {
                output.append("  ").append(index + 1).append(". ").append(report.nextSteps().get(index)).append('\n');
            }
        }
        output.append("\nThis blocker report inspected build metadata statically and did not execute Maven or Gradle.\n");
        return output.toString();
    }

    private static void appendFindingDetail(StringBuilder output, MigrationReadinessFinding finding) {
        if (!finding.project().isBlank()) {
            output.append(" [project: ").append(finding.project()).append(']');
        }
        if (!finding.message().isBlank()) {
            output.append(" - ").append(finding.message());
        }
    }

    public String json(MigrationBlockerReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "command", "explain-blockers", true);
        stringField(json, 1, "source", report.source(), true);
        stringField(json, 1, "root", path(report.root()), true);
        stringField(json, 1, "status", report.status(), true);
        findings(json, report.findings());
        comma(json);
        stringArrayField(json, 1, "nextSteps", report.nextSteps(), false);
        json.append("}\n");
        return json.toString();
    }

    private static void findings(StringBuilder json, List<MigrationReadinessFinding> findings) {
        indent(json, 1).append("\"findings\": [");
        if (!findings.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < findings.size(); index++) {
                MigrationReadinessFinding finding = findings.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "category", finding.category().label(), true);
                stringField(json, 3, "severity", finding.severity().name().toLowerCase(), true);
                stringField(json, 3, "sourcePattern", MigrationBlockerReports.sourcePattern(finding), true);
                stringField(json, 3, "zoltPrimitive", finding.zoltPrimitive(), true);
                stringField(json, 3, "followUp", finding.followUp(), true);
                stringField(json, 3, "signalId", finding.signalId(), true);
                stringField(json, 3, "project", finding.project(), true);
                stringField(json, 3, "message", finding.message(), true);
                stringField(json, 3, "nextStep", finding.nextStep(), false);
                indent(json, 2).append("}");
                if (index < findings.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, values.get(index));
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ");
        string(json, value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void field(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void comma(StringBuilder json) {
        json.append(",\n");
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }
}
