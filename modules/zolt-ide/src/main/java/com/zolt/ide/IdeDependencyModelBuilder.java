package com.zolt.ide;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class IdeDependencyModelBuilder {
    IdeModel.DependencyInfo build(ProjectConfig config) {
        if (config == null) {
            return new IdeModel.DependencyInfo(
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of(),
                    List.of());
        }
        return new IdeModel.DependencyInfo(
                config.versionAliases(),
                dependencyDeclarations(
                        config,
                        "api.dependencies",
                        config.apiDependencies(),
                        config.managedApiDependencies(),
                        config.workspaceApiDependencies()),
                dependencyDeclarations(
                        config,
                        "dependencies",
                        config.dependencies(),
                        config.managedDependencies(),
                        config.workspaceDependencies()),
                dependencyDeclarations(
                        config,
                        "runtime.dependencies",
                        config.runtimeDependencies(),
                        config.managedRuntimeDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config,
                        "provided.dependencies",
                        config.providedDependencies(),
                        config.managedProvidedDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config,
                        "dev.dependencies",
                        config.devDependencies(),
                        config.managedDevDependencies(),
                        Map.of()),
                dependencyDeclarations(
                        config,
                        "test.dependencies",
                        config.testDependencies(),
                        config.managedTestDependencies(),
                        config.workspaceTestDependencies()),
                dependencyDeclarations(
                        config,
                        "annotationProcessors",
                        config.annotationProcessors(),
                        config.managedAnnotationProcessors(),
                        config.workspaceAnnotationProcessors()),
                dependencyDeclarations(
                        config,
                        "test.annotationProcessors",
                        config.testAnnotationProcessors(),
                        config.managedTestAnnotationProcessors(),
                        config.workspaceTestAnnotationProcessors()));
    }

    private static List<IdeModel.DependencyDeclaration> dependencyDeclarations(
            ProjectConfig config,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        List<IdeModel.DependencyDeclaration> declarations = new ArrayList<>();
        for (Map.Entry<String, String> entry : versioned.entrySet()) {
            declarations.add(dependencyDeclaration(config, section, entry.getKey(), entry.getValue(), false, null));
        }
        for (String coordinate : managed) {
            declarations.add(dependencyDeclaration(config, section, coordinate, null, true, null));
        }
        for (Map.Entry<String, String> entry : workspace.entrySet()) {
            declarations.add(dependencyDeclaration(config, section, entry.getKey(), null, false, entry.getValue()));
        }
        for (DependencyMetadata metadata : config.dependencyMetadata().values()) {
            if (metadata.section().equals(section)
                    && metadata.publishOnly()
                    && !versioned.containsKey(metadata.coordinate())
                    && !managed.contains(metadata.coordinate())
                    && !workspace.containsKey(metadata.coordinate())) {
                declarations.add(dependencyDeclaration(
                        config,
                        section,
                        metadata.coordinate(),
                        metadata.version(),
                        metadata.managed(),
                        metadata.workspace()));
            }
        }
        return declarations.stream()
                .sorted(Comparator.comparing(IdeModel.DependencyDeclaration::coordinate))
                .toList();
    }

    private static IdeModel.DependencyDeclaration dependencyDeclaration(
            ProjectConfig config,
            String section,
            String coordinate,
            String version,
            boolean managed,
            String workspace) {
        DependencyMetadata metadata = config.dependencyMetadata().get(DependencyMetadata.key(section, coordinate));
        if (metadata == null) {
            return new IdeModel.DependencyDeclaration(coordinate, version, null, managed, workspace, false, false, List.of());
        }
        return new IdeModel.DependencyDeclaration(
                coordinate,
                version == null ? metadata.version() : version,
                metadata.versionRef(),
                managed || metadata.managed(),
                workspace == null ? metadata.workspace() : workspace,
                metadata.optional(),
                metadata.publishOnly(),
                metadata.exclusions().stream()
                        .map(exclusion -> exclusion.group() + ":" + exclusion.artifact())
                        .toList());
    }
}
