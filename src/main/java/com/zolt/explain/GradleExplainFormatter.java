package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public final class GradleExplainFormatter {
    public String text(GradleInspectionResult result) {
        StringBuilder output = new StringBuilder();
        output.append("Zolt explain: Gradle project\n\n");
        output.append("Project\n");
        output.append("  Root: ").append(result.root()).append('\n');
        output.append("  Settings: ").append(result.settingsFile().isBlank() ? "none" : result.settingsFile()).append('\n');
        output.append("  Included projects: ").append(result.includedProjects().size()).append('\n');
        output.append("  Projects: ").append(result.projects().size()).append('\n');
        output.append("  Version catalog aliases: ").append(result.versionCatalogAliases().size()).append('\n');
        output.append("  Signals: ").append(result.signals().size()).append('\n');

        output.append("\nProjects\n");
        for (GradleProjectInspection project : result.projects()) {
            output.append("  - ").append(path(project.path()))
                    .append(" (").append(project.name())
                    .append(", dsl=").append(project.dsl())
                    .append(", java=").append(project.javaVersion())
                    .append(")\n");
            output.append("    build file: ").append(project.buildFile()).append('\n');
            output.append("    plugins: ").append(project.plugins().size()).append('\n');
            output.append("    repositories: ").append(project.repositories().size()).append('\n');
            output.append("    dependencies: ").append(project.dependencies().size()).append('\n');
        }

        signalSection(output, "What Zolt can build", result.signals(), ExplainSignal.Category.BUILDABILITY);
        signalSection(output, "What can cache", result.signals(), ExplainSignal.Category.CACHEABILITY);
        signalSection(output, "Non-determinism", result.signals(), ExplainSignal.Category.NON_DETERMINISM);
        signalSection(output, "Migration blockers", result.signals(), ExplainSignal.Category.MIGRATION_BLOCKER);
        nextSteps(output, result.signals());
        output.append("\nThis command inspected Gradle metadata statically and did not execute Gradle.\n");
        return output.toString();
    }

    public String json(GradleInspectionResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "source", "gradle", true);
        stringField(json, 1, "root", path(result.root()), true);
        stringField(json, 1, "settingsFile", result.settingsFile(), true);
        summary(json, result);
        comma(json);
        stringArrayField(json, 1, "includedProjects", result.includedProjects(), true);
        catalogAliases(json, result.versionCatalogAliases());
        projects(json, result.projects());
        comma(json);
        signals(json, result.signals());
        comma(json);
        migration(json, result.signals());
        json.append("\n}\n");
        return json.toString();
    }

    private static void summary(StringBuilder json, GradleInspectionResult result) {
        indent(json, 1).append("\"summary\": {\n");
        field(json, 2, "includedProjects", result.includedProjects().size(), true);
        field(json, 2, "projects", result.projects().size(), true);
        field(json, 2, "versionCatalogAliases", result.versionCatalogAliases().size(), true);
        field(json, 2, "signals", result.signals().size(), true);
        field(json, 2, "blockers", count(result.signals(), ExplainSignal.Severity.BLOCK), true);
        field(json, 2, "warnings", count(result.signals(), ExplainSignal.Severity.WARN), true);
        field(json, 2, "unknown", count(result.signals(), ExplainSignal.Severity.UNKNOWN), true);
        field(json, 2, "ok", result.signals().isEmpty() ? 1 : 0, false);
        indent(json, 1).append("}");
    }

    private static void catalogAliases(StringBuilder json, List<GradleVersionCatalogAlias> aliases) {
        indent(json, 1).append("\"versionCatalogAliases\": [");
        if (!aliases.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < aliases.size(); index++) {
                GradleVersionCatalogAlias alias = aliases.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "alias", alias.alias(), true);
                stringField(json, 3, "coordinate", alias.coordinate(), false);
                indent(json, 2).append("}");
                if (index < aliases.size() - 1) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
        comma(json);
    }

    private static void projects(StringBuilder json, List<GradleProjectInspection> projects) {
        indent(json, 1).append("\"projects\": [");
        if (!projects.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < projects.size(); index++) {
                GradleProjectInspection project = projects.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "path", path(project.path()), true);
                stringField(json, 3, "name", project.name(), true);
                stringField(json, 3, "buildFile", project.buildFile(), true);
                stringField(json, 3, "dsl", project.dsl(), true);
                stringField(json, 3, "javaVersion", project.javaVersion(), true);
                pluginArray(json, 3, "plugins", project.plugins(), true);
                repositoryArray(json, 3, "repositories", project.repositories(), true);
                dependencyArray(json, 3, "dependencies", project.dependencies(), true);
                stringArrayField(json, 3, "sourceRoots", project.sourceRoots(), true);
                stringArrayField(json, 3, "testSourceRoots", project.testSourceRoots(), false);
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

    private static void migration(StringBuilder json, List<ExplainSignal> signals) {
        indent(json, 1).append("\"migration\": {\n");
        stringField(json, 2, "status", migrationStatus(signals), true);
        stringArrayField(json, 2, "nextSteps", nextStepValues(signals), false);
        indent(json, 1).append("}");
    }

    private static void signalSection(
            StringBuilder output,
            String title,
            List<ExplainSignal> signals,
            ExplainSignal.Category category) {
        output.append('\n').append(title).append('\n');
        List<ExplainSignal> categorySignals = signals.stream()
                .filter(signal -> signal.category() == category)
                .toList();
        if (categorySignals.isEmpty()) {
            output.append("  ok    no static ").append(categoryLabel(category)).append(" issues found in this first inspection pass\n");
            return;
        }
        for (ExplainSignal signal : categorySignals) {
            output.append("  ")
                    .append(signal.severity().name().toLowerCase())
                    .append("  ")
                    .append(signal.message())
                    .append('\n');
        }
    }

    private static void nextSteps(StringBuilder output, List<ExplainSignal> signals) {
        output.append("\nNext steps\n");
        List<String> steps = nextStepValues(signals);
        for (int index = 0; index < steps.size(); index++) {
            output.append("  ").append(index + 1).append(". ").append(steps.get(index)).append('\n');
        }
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

    private static String categoryLabel(ExplainSignal.Category category) {
        return category.name().toLowerCase().replace('_', '-');
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

    private static void pluginArray(
            StringBuilder json,
            int level,
            String name,
            List<GradlePluginInspection> plugins,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!plugins.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < plugins.size(); index++) {
                GradlePluginInspection plugin = plugins.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "id", plugin.id(), true);
                stringField(json, level + 2, "version", plugin.version(), false);
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

    private static void repositoryArray(
            StringBuilder json,
            int level,
            String name,
            List<GradleRepositoryInspection> repositories,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!repositories.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < repositories.size(); index++) {
                GradleRepositoryInspection repository = repositories.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "kind", repository.kind(), true);
                stringField(json, level + 2, "url", repository.url(), false);
                indent(json, level + 1).append("}");
                if (index < repositories.size() - 1) {
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

    private static void dependencyArray(
            StringBuilder json,
            int level,
            String name,
            List<GradleDependencyInspection> dependencies,
            boolean trailingComma) {
        indent(json, level).append('"').append(name).append("\": [");
        if (!dependencies.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < dependencies.size(); index++) {
                GradleDependencyInspection dependency = dependencies.get(index);
                indent(json, level + 1).append("{\n");
                stringField(json, level + 2, "configuration", dependency.configuration(), true);
                stringField(json, level + 2, "notation", dependency.notation(), true);
                stringField(json, level + 2, "resolvedCoordinate", dependency.resolvedCoordinate(), true);
                stringField(json, level + 2, "versionCatalogAlias", dependency.versionCatalogAlias(), false);
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
