package com.zolt.explain;

import java.nio.file.Path;
import java.util.List;

public record MavenInspectionResult(
        Path root,
        List<MavenProjectInspection> projects,
        List<ExplainSignal> signals) {
    public MavenInspectionResult {
        projects = List.copyOf(projects);
        signals = List.copyOf(signals);
    }
}
