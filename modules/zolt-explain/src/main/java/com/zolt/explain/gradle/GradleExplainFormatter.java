package com.zolt.explain.gradle;

import com.zolt.explain.ExplainSignal;
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
            if (project.group().isPresent() && project.version().isPresent()) {
                output.append("    coordinates: ")
                        .append(project.group().orElseThrow())
                        .append(':')
                        .append(project.name())
                        .append(':')
                        .append(project.version().orElseThrow())
                        .append('\n');
            } else {
                project.group().ifPresent(group -> output.append("    group: ").append(group).append('\n'));
                project.version().ifPresent(version -> output.append("    version: ").append(version).append('\n'));
            }
            project.mainClass().ifPresent(mainClass -> output.append("    main class: ").append(mainClass).append('\n'));
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
        return new GradleExplainJsonWriter().json(result);
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

    private static String categoryLabel(ExplainSignal.Category category) {
        return category.name().toLowerCase().replace('_', '-');
    }

    private static String path(Path path) {
        return path.toString().replace('\\', '/');
    }

}
