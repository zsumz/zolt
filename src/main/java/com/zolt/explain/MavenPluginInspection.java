package com.zolt.explain;

import java.util.List;

public record MavenPluginInspection(
        String coordinate,
        List<String> phases,
        boolean pluginManagement) {
    public MavenPluginInspection {
        phases = List.copyOf(phases);
    }
}
