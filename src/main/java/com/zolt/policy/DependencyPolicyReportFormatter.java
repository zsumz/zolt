package com.zolt.policy;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

public final class DependencyPolicyReportFormatter {
    public String text(DependencyPolicyReport report) {
        StringBuilder output = new StringBuilder();
        output.append("Dependency policy diagnostics\n");
        output.append("Project: ").append(report.projectRoot()).append('\n');
        output.append("Platforms: ").append(report.platforms().size()).append('\n');
        for (DependencyPolicyReport.PlatformPolicyDiagnostic platform : report.platforms()) {
            output.append("- ")
                    .append(platform.platform())
                    .append(" manages ")
                    .append(platform.packages().size())
                    .append(" selected packages")
                    .append('\n');
            for (DependencyPolicyReport.ManagedPackageDiagnostic managedPackage : platform.packages()) {
                output.append("  - ")
                        .append(managedPackage.coordinate())
                        .append(':')
                        .append(managedPackage.version())
                        .append(" [")
                        .append(managedPackage.scope())
                        .append("] ")
                        .append(managedPackage.policy())
                        .append('\n');
            }
        }

        output.append("Constraints: ").append(report.constraints().size()).append('\n');
        for (DependencyPolicyReport.ConstraintPolicyDiagnostic constraint : report.constraints()) {
            output.append("- ")
                    .append(constraint.coordinate())
                    .append(" ")
                    .append(constraint.kind())
                    .append(" ")
                    .append(constraint.requestedVersion())
                    .append(" status=")
                    .append(constraint.status());
            constraint.selectedVersion().ifPresent(version -> output.append(" selected=").append(version));
            constraint.source().ifPresent(source -> output.append(" source=").append(source));
            constraint.reason().ifPresent(reason -> output.append(" reason=").append(reason));
            output.append('\n');
            for (String policy : constraint.policies()) {
                output.append("  - ").append(policy).append('\n');
            }
        }

        output.append("Exclusions: ").append(report.exclusions().size()).append('\n');
        for (DependencyPolicyReport.ExclusionPolicyDiagnostic exclusion : report.exclusions()) {
            output.append("- ")
                    .append(exclusion.coordinate())
                    .append(" status=")
                    .append(exclusion.status());
            exclusion.reason().ifPresent(reason -> output.append(" reason=").append(reason));
            output.append('\n');
            for (String source : exclusion.sources()) {
                output.append("  source ").append(source).append('\n');
            }
            for (String policy : exclusion.policies()) {
                output.append("  - ").append(policy).append('\n');
            }
        }

        output.append("Direct versions: ").append(report.directVersions().size()).append('\n');
        for (DependencyPolicyReport.DirectVersionDiagnostic direct : report.directVersions()) {
            output.append("- ")
                    .append(direct.section())
                    .append(' ')
                    .append(direct.coordinate())
                    .append(':')
                    .append(direct.version())
                    .append(" status=")
                    .append(direct.status())
                    .append('\n');
        }
        return output.toString();
    }

    public String json(DependencyPolicyReport report) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        pathField(json, 1, "projectRoot", report.projectRoot(), true);
        platforms(json, report.platforms());
        json.append(",\n");
        constraints(json, report.constraints());
        json.append(",\n");
        exclusions(json, report.exclusions());
        json.append(",\n");
        directVersions(json, report.directVersions());
        json.append("\n}\n");
        return json.toString();
    }

    private static void platforms(
            StringBuilder json,
            List<DependencyPolicyReport.PlatformPolicyDiagnostic> platforms) {
        indent(json, 1).append("\"platforms\": [");
        if (!platforms.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < platforms.size(); index++) {
                DependencyPolicyReport.PlatformPolicyDiagnostic platform = platforms.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "platform", platform.platform(), true);
                managedPackages(json, platform.packages());
                json.append('\n');
                indent(json, 2).append("}");
                if (index + 1 < platforms.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void managedPackages(
            StringBuilder json,
            List<DependencyPolicyReport.ManagedPackageDiagnostic> packages) {
        indent(json, 3).append("\"packages\": [");
        if (!packages.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < packages.size(); index++) {
                DependencyPolicyReport.ManagedPackageDiagnostic managedPackage = packages.get(index);
                indent(json, 4).append("{\n");
                stringField(json, 5, "coordinate", managedPackage.coordinate(), true);
                stringField(json, 5, "version", managedPackage.version(), true);
                stringField(json, 5, "scope", managedPackage.scope(), true);
                stringField(json, 5, "policy", managedPackage.policy(), false);
                indent(json, 4).append("}");
                if (index + 1 < packages.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 3);
        }
        json.append("]");
    }

    private static void constraints(
            StringBuilder json,
            List<DependencyPolicyReport.ConstraintPolicyDiagnostic> constraints) {
        indent(json, 1).append("\"constraints\": [");
        if (!constraints.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < constraints.size(); index++) {
                DependencyPolicyReport.ConstraintPolicyDiagnostic constraint = constraints.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "coordinate", constraint.coordinate(), true);
                stringField(json, 3, "kind", constraint.kind(), true);
                stringField(json, 3, "requestedVersion", constraint.requestedVersion(), true);
                optionalStringField(json, 3, "selectedVersion", constraint.selectedVersion(), true);
                stringField(json, 3, "status", constraint.status(), true);
                optionalStringField(json, 3, "source", constraint.source(), true);
                optionalStringField(json, 3, "reason", constraint.reason(), true);
                stringArrayField(json, 3, "policies", constraint.policies(), false);
                indent(json, 2).append("}");
                if (index + 1 < constraints.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void exclusions(
            StringBuilder json,
            List<DependencyPolicyReport.ExclusionPolicyDiagnostic> exclusions) {
        indent(json, 1).append("\"exclusions\": [");
        if (!exclusions.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < exclusions.size(); index++) {
                DependencyPolicyReport.ExclusionPolicyDiagnostic exclusion = exclusions.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "coordinate", exclusion.coordinate(), true);
                stringField(json, 3, "status", exclusion.status(), true);
                optionalStringField(json, 3, "reason", exclusion.reason(), true);
                stringArrayField(json, 3, "sources", exclusion.sources(), true);
                stringArrayField(json, 3, "policies", exclusion.policies(), false);
                indent(json, 2).append("}");
                if (index + 1 < exclusions.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void directVersions(
            StringBuilder json,
            List<DependencyPolicyReport.DirectVersionDiagnostic> directVersions) {
        indent(json, 1).append("\"directVersions\": [");
        if (!directVersions.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < directVersions.size(); index++) {
                DependencyPolicyReport.DirectVersionDiagnostic direct = directVersions.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "section", direct.section(), true);
                stringField(json, 3, "coordinate", direct.coordinate(), true);
                stringField(json, 3, "version", direct.version(), true);
                stringField(json, 3, "status", direct.status(), false);
                indent(json, 2).append("}");
                if (index + 1 < directVersions.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void pathField(StringBuilder json, int level, String name, Path value, boolean trailingComma) {
        stringField(json, level, name, value.toAbsolutePath().normalize().toString(), trailingComma);
    }

    private static void optionalStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        if (value.isPresent()) {
            string(json, value.orElseThrow());
        } else {
            json.append("null");
        }
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
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
        indent(json, level);
        string(json, name);
        json.append(": [");
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

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
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

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }
}
