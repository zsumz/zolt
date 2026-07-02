package com.zolt.explain.maven;

import com.zolt.explain.ExplainSignal;
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
                    .append(" (").append(project.artifactId())
                    .append(", packaging=").append(project.packaging())
                    .append(", java=").append(project.javaVersion())
                    .append(")\n");
            if (!project.name().isBlank()) {
                output.append("    name: ").append(project.name()).append('\n');
            }
            output.append("    coordinates: ")
                    .append(coordinate(project.groupId()))
                    .append(':').append(project.artifactId())
                    .append(':').append(coordinate(project.version()))
                    .append('\n');
            if (!project.modules().isEmpty()) {
                output.append("    modules: ").append(String.join(", ", project.modules())).append('\n');
            }
            output.append("    dependencies: ").append(project.dependencies().size()).append('\n');
            for (MavenDependencyInspection dependency : project.dependencies()) {
                output.append("      - ")
                        .append(dependency.scope())
                        .append(' ')
                        .append(dependency.coordinate())
                        .append('\n');
            }
            output.append("    managed dependencies: ").append(project.dependencyManagement().size()).append('\n');
            output.append("    imported BOMs: ").append(project.importedBoms().size()).append('\n');
            output.append("    annotation processors: ").append(project.annotationProcessors().size()).append('\n');
            for (MavenAnnotationProcessorInspection processor : project.annotationProcessors()) {
                output.append("      - ")
                        .append(processor.coordinate())
                        .append('\n');
            }
            output.append("    plugins: ").append(project.plugins().size()).append('\n');
            output.append("    profiles: ").append(project.profiles().size()).append('\n');
        }

        signalSection(output, "What Zolt can build", result.signals(), ExplainSignal.Category.BUILDABILITY);
        signalSection(output, "What can cache", result.signals(), ExplainSignal.Category.CACHEABILITY);
        signalSection(output, "Non-determinism", result.signals(), ExplainSignal.Category.NON_DETERMINISM);
        signalSection(output, "Migration blockers", result.signals(), ExplainSignal.Category.MIGRATION_BLOCKER);
        nextSteps(output, result.signals());
        output.append("\nThis command inspected Maven metadata statically and did not execute Maven.\n");
        return output.toString();
    }

    public String json(MavenInspectionResult result) {
        return new MavenExplainJsonWriter().json(result);
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

    private static String coordinate(String value) {
        return value == null || value.isBlank() ? "(unknown)" : value;
    }

}
