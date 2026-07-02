package sh.zolt.explain.gradle;

import sh.zolt.explain.ExplainSignal;
import java.nio.file.Path;
import java.util.List;

public record GradleInspectionResult(
        Path root,
        String settingsFile,
        List<String> includedProjects,
        List<GradleVersionCatalogAlias> versionCatalogAliases,
        List<GradleProjectInspection> projects,
        List<ExplainSignal> signals) {
    public GradleInspectionResult {
        includedProjects = List.copyOf(includedProjects);
        versionCatalogAliases = List.copyOf(versionCatalogAliases);
        projects = List.copyOf(projects);
        signals = List.copyOf(signals);
    }
}
