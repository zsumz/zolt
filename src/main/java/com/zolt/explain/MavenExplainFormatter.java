package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public final class MavenExplainFormatter {
    public String text(MavenInspectionResult result) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt explain: Maven project\n\n");
        output.append("Project\n");
        output.append("  Root: ").append(result.root()).append('\n');
        output.append("  Projects: ").append(result.projects().size()).append('\n');
        output.append("  Signals: ").append(result.signals().size()).append('\n');

        output.append("\nProjects\n");
        for (MavenProjectInspection project : result.projects()) {
            output.append("  - ").append(path(project.path()))
                    .append(" (").append(project.name())
                    .append(", packaging=").append(project.packaging())
                    .append(", java=").append(project.javaVersion())
                    .append(")\n");
            if (!project.modules().isEmpty()) {
                output.append("    modules: ").append(String.join(", ", project.modules())).append('\n');
            }
            output.append("    dependencies: ").append(project.dependencies().size()).append('\n');
            output.append("    managed dependencies: ").append(project.dependencyManagement().size()).append('\n');
            output.append("    imported BOMs: ").append(project.importedBoms().size()).append('\n');
            output.append("    plugins: ").append(project.plugins().size()).append('\n');
            output.append("    profiles: ").append(project.profiles().size()).append('\n');
        }

        output.append("\nSignals\n");
        if (result.signals().isEmpty()) {
            output.append("  ok    no static Maven blockers found in this first inspection pass\n");
        } else {
            for (ExplainSignal signal : result.signals()) {
                output.append("  ")
                        .append(signal.severity().name().toLowerCase())
                        .append("  ")
                        .append(signal.message())
                        .append('\n');
            }
        }
        output.append("\nThis command inspected Maven metadata statically and did not execute Maven.\n");
        return output.toString();
    }

    public String json(MavenInspectionResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "source", "maven", true);
        stringField(json, 1, "root", path(result.root()), true);
        summary(json, result);
        comma(json);
        projects(json, result.projects());
        comma(json);
        signals(json, result.signals());
        json.append("\n}\n");
        return json.toString();
    }

    private static void summary(StringBuilder json, MavenInspectionResult result) {
        indent(json, 1).append("\"summary\": {\n");
        field(json, 2, "projects", result.projects().size(), true);
        field(json, 2, "signals", result.signals().size(), true);
        field(json, 2, "blockers", count(result.signals(), ExplainSignal.Severity.BLOCK), true);
        field(json, 2, "warnings", count(result.signals(), ExplainSignal.Severity.WARN), false);
        indent(json, 1).append("}");
    }

    private static void projects(StringBuilder json, List<MavenProjectInspection> projects) {
        indent(json, 1).append("\"projects\": [");
        if (!projects.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < projects.size(); index++) {
                MavenProjectInspection project = projects.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "path", path(project.path()), true);
                stringField(json, 3, "name", project.name(), true);
                stringField(json, 3, "packaging", project.packaging(), true);
                stringField(json, 3, "javaVersion", project.javaVersion(), true);
                stringArrayField(json, 3, "modules", project.modules(), true);
                stringArrayField(json, 3, "sourceRoots", project.sourceRoots(), true);
                stringArrayField(json, 3, "testSourceRoots", project.testSourceRoots(), true);
                stringArrayField(json, 3, "resourceRoots", project.resourceRoots(), true);
                dependencyArray(json, 3, "dependencies", project.dependencies(), true);
                dependencyArray(json, 3, "dependencyManagement", project.dependencyManagement(), true);
                dependencyArray(json, 3, "importedBoms", project.importedBoms(), true);
                pluginArray(json, 3, "plugins", project.plugins(), true);
                profileArray(json, 3, "profiles", project.profiles(), false);
                indent(json, 2).append("}");
                if (index < projects.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
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

    private static void dependencyArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenDependencyInspection> dependencies,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                MavenDependencyInspection dependency = dependencies.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "scope", dependency.scope(), true);
                stringField(json, level + 2, "coordinate", dependency.coordinate(), true);
                stringField(json, level + 2, "version", dependency.version(), true);
                stringField(json, level + 2, "type", dependency.type(), true);
                field(json, level + 2, "optional", dependency.optional(), true);
                field(json, level + 2, "managed", dependency.managed(), true);
                field(json, level + 2, "importedBom", dependency.importedBom(), false);
                indent(json, level + 1).append("}");
                if (index < dependencies.size() - 1) {
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

    private static void pluginArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenPluginInspection> plugins,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!plugins.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < plugins.size(); index++) {
                MavenPluginInspection plugin = plugins.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "coordinate", plugin.coordinate(), true);
                stringArrayField(json, level + 2, "phases", plugin.phases(), true);
                field(json, level + 2, "pluginManagement", plugin.pluginManagement(), false);
                indent(json, level + 1).append("}");
                if (index < plugins.size() - 1) {
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

    private static void profileArray(
            StringBuilder json,
            int level,
            String name,
            List<MavenProfileInspection> profiles,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!profiles.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < profiles.size(); index++) {
                MavenProfileInspection profile = profiles.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "id", profile.id(), true);
                stringArrayField(json, level + 2, "activationHints", profile.activationHints(), false);
                indent(json, level + 1).append("}");
                if (index < profiles.size() - 1) {
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

    private static void field(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static int count(List<ExplainSignal> signals, ExplainSignal.Severity severity) {
        return (int) signals.stream().filter(signal -> signal.severity() == severity).count();
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
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

    private static void comma(StringBuilder json) {
        json.append(",\n");
    }
}
