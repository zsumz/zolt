package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public final class MigrationReadinessScorecardFormatter {
    public String text(MigrationReadinessScorecard scorecard) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt migration readiness scorecard: ")
                .append(scorecard.source())
                .append(" project\n\n");
        output.append("Project\n");
        output.append("  Root: ").append(scorecard.root()).append('\n');
        output.append("  Status: ").append(scorecard.status()).append('\n');
        output.append("  Concerns: ").append(scorecard.concerns().size()).append('\n');

        output.append("\nReadiness\n");
        for (MigrationReadinessConcern concern : scorecard.concerns()) {
            output.append("  - ")
                    .append(concern.name())
                    .append(": ")
                    .append(concern.status())
                    .append('\n');
            for (MigrationReadinessFinding finding : concern.findings()) {
                output.append("      ")
                        .append(finding.category().label())
                        .append("  ")
                        .append(stripConcernPrefix(finding.sourcePattern()))
                        .append(" -> ")
                        .append(finding.zoltPrimitive());
                if (!finding.followUp().isBlank()) {
                    output.append(" (").append(finding.followUp()).append(')');
                }
                appendFindingDetail(output, finding);
                output.append('\n');
                if (!finding.signalId().isBlank()) {
                    output.append("        signal: ")
                            .append(finding.signalId())
                            .append(", severity: ")
                            .append(finding.severity().name().toLowerCase())
                            .append('\n');
                }
            }
        }

        output.append("\nMigration checklist\n");
        if (scorecard.checklist().isEmpty()) {
            output.append("  1. Create zolt.toml, then run zolt resolve, zolt plan, and zolt check.\n");
        } else {
            for (int index = 0; index < scorecard.checklist().size(); index++) {
                output.append("  ").append(index + 1).append(". ").append(scorecard.checklist().get(index)).append('\n');
            }
        }
        output.append("\nThis scorecard inspected build metadata statically and did not execute Maven or Gradle.\n");
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

    public String json(MigrationReadinessScorecard scorecard) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "command", "explain-scorecard", true);
        stringField(json, 1, "source", scorecard.source(), true);
        stringField(json, 1, "root", path(scorecard.root()), true);
        stringField(json, 1, "status", scorecard.status(), true);
        concerns(json, scorecard.concerns());
        comma(json);
        stringArrayField(json, 1, "checklist", scorecard.checklist(), false);
        json.append("}\n");
        return json.toString();
    }

    private static void concerns(StringBuilder json, List<MigrationReadinessConcern> concerns) {
        indent(json, 1).append("\"concerns\": [");
        if (!concerns.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < concerns.size(); index++) {
                MigrationReadinessConcern concern = concerns.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "name", concern.name(), true);
                stringField(json, 3, "status", concern.status(), true);
                findings(json, 3, concern.findings(), false);
                indent(json, 2).append("}");
                if (index < concerns.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void findings(
            StringBuilder json,
            int level,
            List<MigrationReadinessFinding> findings,
            boolean trailingComma) {
        indent(json, level).append("\"findings\": [");
        if (!findings.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < findings.size(); index++) {
                MigrationReadinessFinding finding = findings.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "category", finding.category().label(), true);
                stringField(json, level + 2, "severity", finding.severity().name().toLowerCase(), true);
                stringField(json, level + 2, "sourcePattern", stripConcernPrefix(finding.sourcePattern()), true);
                stringField(json, level + 2, "zoltPrimitive", finding.zoltPrimitive(), true);
                stringField(json, level + 2, "followUp", finding.followUp(), true);
                stringField(json, level + 2, "signalId", finding.signalId(), true);
                stringField(json, level + 2, "project", finding.project(), true);
                stringField(json, level + 2, "message", finding.message(), true);
                stringField(json, level + 2, "nextStep", finding.nextStep(), false);
                indent(json, level + 1).append("}");
                if (index < findings.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, level);
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
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

    private static String stripConcernPrefix(String sourcePattern) {
        if (!sourcePattern.startsWith("concern:")) {
            return sourcePattern;
        }
        int end = sourcePattern.indexOf(' ');
        return sourcePattern.substring(end + 1);
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
