package com.zolt.explain;

import java.util.List;

public record MavenPluginInspection(
        String coordinate,
        List<String> phases,
        List<String> goals,
        boolean pluginManagement) {
    public MavenPluginInspection {
        phases = List.copyOf(phases);
        goals = List.copyOf(goals);
    }

    public MavenPluginInspection(String coordinate, List<String> phases, boolean pluginManagement) {
        this(coordinate, phases, List.of(), pluginManagement);
    }
}
