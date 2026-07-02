package com.zolt.explain.maven;

import java.util.List;

public record MavenPluginInspection(
        String coordinate,
        List<String> phases,
        List<String> goals,
        List<String> disabledExecutions,
        boolean pluginManagement) {
    public MavenPluginInspection {
        phases = List.copyOf(phases);
        goals = List.copyOf(goals);
        disabledExecutions = List.copyOf(disabledExecutions);
    }

    public MavenPluginInspection(
            String coordinate,
            List<String> phases,
            List<String> goals,
            boolean pluginManagement) {
        this(coordinate, phases, goals, List.of(), pluginManagement);
    }

    public MavenPluginInspection(String coordinate, List<String> phases, boolean pluginManagement) {
        this(coordinate, phases, List.of(), List.of(), pluginManagement);
    }
}
