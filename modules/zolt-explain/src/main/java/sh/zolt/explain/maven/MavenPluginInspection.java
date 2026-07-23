package sh.zolt.explain.maven;

import java.util.List;

public record MavenPluginInspection(
        String coordinate,
        List<String> phases,
        List<String> goals,
        List<String> disabledExecutions,
        boolean pluginManagement,
        List<MavenExecInvocation> execInvocations) {
    public MavenPluginInspection {
        phases = List.copyOf(phases);
        goals = List.copyOf(goals);
        disabledExecutions = List.copyOf(disabledExecutions);
        execInvocations = execInvocations == null ? List.of() : List.copyOf(execInvocations);
    }

    public MavenPluginInspection(
            String coordinate,
            List<String> phases,
            List<String> goals,
            List<String> disabledExecutions,
            boolean pluginManagement) {
        this(coordinate, phases, goals, disabledExecutions, pluginManagement, List.of());
    }

    public MavenPluginInspection(
            String coordinate,
            List<String> phases,
            List<String> goals,
            boolean pluginManagement) {
        this(coordinate, phases, goals, List.of(), pluginManagement, List.of());
    }

    public MavenPluginInspection(String coordinate, List<String> phases, boolean pluginManagement) {
        this(coordinate, phases, List.of(), List.of(), pluginManagement, List.of());
    }
}
