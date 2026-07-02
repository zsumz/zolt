package sh.zolt.build.packageplan;

import java.nio.file.Path;
import java.util.List;

public final class PackagePlanFormatter {
    public String text(PackagePlan plan) {
        StringBuilder output = new StringBuilder();
        output.append("Package plan\n");
        output.append("Mode: ").append(plan.mode().configValue()).append('\n');
        output.append("Archive: ").append(plan.archivePath()).append('\n');
        output.append("Application output: ").append(plan.applicationOutput()).append('\n');
        output.append("Application layout: ").append(plan.applicationLayout()).append('\n');
        plan.runtimeClasspathPath().ifPresent(path -> output.append("Runtime classpath sidecar: ").append(path).append('\n'));
        output.append("Dependencies: ").append(plan.dependencies().size()).append('\n');
        for (PackagePlanDependency dependency : plan.dependencies()) {
            output.append("- ")
                    .append(dependency.coordinate())
                    .append(" [")
                    .append(dependency.scope().lockfileName())
                    .append("] ")
                    .append(dependency.disposition());
            if (!dependency.location().isBlank()) {
                output.append(" -> ").append(dependency.location());
            }
            output.append(" rule=").append(dependency.ruleName());
            output.append(" lanes=").append(String.join(",", dependency.lanes()));
            output.append(" packageDefault=").append(dependency.packageDefault());
            output.append(" lane=").append(dependency.laneDisposition());
            output.append(" (").append(dependency.reason()).append(")");
            if (!dependency.policies().isEmpty()) {
                output.append(" policies=").append(String.join("; ", dependency.policies()));
            }
            output.append('\n');
        }
        for (PackagePlanWarning warning : plan.warnings()) {
            output.append("warning ")
                    .append(warning.code())
                    .append(' ')
                    .append(warning.subject())
                    .append(" rule=")
                    .append(warning.ruleName())
                    .append(' ')
                    .append(warning.message())
                    .append('\n')
                    .append("  next: ")
                    .append(warning.nextStep())
                    .append('\n');
        }
        return output.toString();
    }

    public String json(PackagePlan plan) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        stringField(json, 1, "mode", plan.mode().configValue(), true);
        pathField(json, 1, "archive", plan.archivePath(), true);
        pathField(json, 1, "applicationOutput", plan.applicationOutput(), true);
        stringField(json, 1, "applicationLayout", plan.applicationLayout(), true);
        indent(json, 1).append("\"runtimeClasspath\": ");
        if (plan.runtimeClasspathPath().isPresent()) {
            string(json, jsonPath(plan.runtimeClasspathPath().orElseThrow()));
        } else {
            json.append("null");
        }
        json.append(",\n");
        dependencies(json, plan.dependencies());
        json.append(",\n");
        warnings(json, plan.warnings());
        json.append("\n}\n");
        return json.toString();
    }

    private static void dependencies(StringBuilder json, List<PackagePlanDependency> dependencies) {
        indent(json, 1).append("\"dependencies\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                PackagePlanDependency dependency = dependencies.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "coordinate", dependency.coordinate(), true);
                stringField(json, 3, "version", dependency.version(), true);
                stringField(json, 3, "scope", dependency.scope().lockfileName(), true);
                stringArrayField(json, 3, "lanes", dependency.lanes(), true);
                field(json, 3, "packageDefault", dependency.packageDefault(), true);
                stringField(json, 3, "laneDisposition", dependency.laneDisposition(), true);
                stringField(json, 3, "disposition", dependency.disposition(), true);
                stringField(json, 3, "rule", dependency.ruleName(), true);
                stringField(json, 3, "location", dependency.location(), true);
                stringField(json, 3, "reason", dependency.reason(), true);
                stringArrayField(json, 3, "policies", dependency.policies(), false);
                indent(json, 2).append("}");
                if (index + 1 < dependencies.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void warnings(StringBuilder json, List<PackagePlanWarning> warnings) {
        indent(json, 1).append("\"warnings\": [");
        if (!warnings.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < warnings.size(); index++) {
                PackagePlanWarning warning = warnings.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "code", warning.code(), true);
                stringField(json, 3, "subject", warning.subject(), true);
                stringField(json, 3, "rule", warning.ruleName(), true);
                stringField(json, 3, "message", warning.message(), true);
                stringField(json, 3, "nextStep", warning.nextStep(), false);
                indent(json, 2).append("}");
                if (index + 1 < warnings.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void pathField(StringBuilder json, int level, String name, Path value, boolean trailingComma) {
        stringField(json, level, name, jsonPath(value), trailingComma);
    }

    private static void field(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
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

    private static String jsonPath(Path path) {
        return path.toAbsolutePath().normalize().toString();
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }
}
