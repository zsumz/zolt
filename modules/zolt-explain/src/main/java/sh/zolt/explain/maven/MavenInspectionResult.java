package sh.zolt.explain.maven;

import sh.zolt.explain.ExplainSignal;
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
