package com.zolt.toml;

import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.ProjectConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

final class DependencySectionWriter {
    private DependencySectionWriter() {
    }

    static void write(StringBuilder toml, ProjectConfig config) {
        writeOptionalDependencies(
                toml,
                "api.dependencies",
                config.apiDependencies(),
                config.managedApiDependencies(),
                config.workspaceApiDependencies(),
                config.dependencyMetadata());
        writeDependencies(
                toml,
                "dependencies",
                config.dependencies(),
                config.managedDependencies(),
                config.workspaceDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "runtime.dependencies",
                config.runtimeDependencies(),
                config.managedRuntimeDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "provided.dependencies",
                config.providedDependencies(),
                config.managedProvidedDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "dev.dependencies",
                config.devDependencies(),
                config.managedDevDependencies(),
                config.dependencyMetadata());
        writeDependencies(
                toml,
                "test.dependencies",
                config.testDependencies(),
                config.managedTestDependencies(),
                config.workspaceTestDependencies(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "annotationProcessors",
                config.annotationProcessors(),
                config.managedAnnotationProcessors(),
                config.dependencyMetadata());
        writeOptionalDependencies(
                toml,
                "test.annotationProcessors",
                config.testAnnotationProcessors(),
                config.managedTestAnnotationProcessors(),
                config.dependencyMetadata());
    }

    private static void writeDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata) {
        toml.append('[').append(section).append("]\n");
        for (String coordinate : sortedCoordinates(versioned, managed, workspace, dependencyMetadata, section)) {
            toml.append(quote(coordinate)).append(" = ");
            DependencyMetadata metadata = dependencyMetadata.get(DependencyMetadata.key(section, coordinate));
            if (metadata != null && (!metadata.emptyMetadata() || metadata.publishOnly())) {
                writeDependencyMetadata(toml, coordinate, versioned, managed, workspace, metadata);
            } else {
                writeSimpleDependency(toml, coordinate, versioned, workspace);
            }
            toml.append('\n');
        }
        toml.append('\n');
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (versioned.isEmpty() && managed.isEmpty() && !hasDependencyMetadata(dependencyMetadata, section)) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, Map.of(), dependencyMetadata);
    }

    private static void writeOptionalDependencies(
            StringBuilder toml,
            String section,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (versioned.isEmpty()
                && managed.isEmpty()
                && workspace.isEmpty()
                && !hasDependencyMetadata(dependencyMetadata, section)) {
            return;
        }
        writeDependencies(toml, section, versioned, managed, workspace, dependencyMetadata);
    }

    private static void writeSimpleDependency(
            StringBuilder toml,
            String coordinate,
            Map<String, String> versioned,
            Map<String, String> workspace) {
        String workspacePath = workspace.get(coordinate);
        if (workspacePath != null) {
            toml.append("{ workspace = ").append(quote(workspacePath)).append(" }");
            return;
        }
        String version = versioned.get(coordinate);
        if (version == null) {
            toml.append("{}");
        } else {
            toml.append(quote(version));
        }
    }

    private static void writeDependencyMetadata(
            StringBuilder toml,
            String coordinate,
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            DependencyMetadata metadata) {
        List<String> parts = new ArrayList<>();
        String version = metadata.version() == null ? versioned.get(coordinate) : metadata.version();
        String workspacePath = metadata.workspace() == null ? workspace.get(coordinate) : metadata.workspace();
        boolean managedDependency = metadata.managed() || managed.contains(coordinate);
        if (metadata.versionRef() != null) {
            parts.add("versionRef = " + quote(metadata.versionRef()));
        } else if (version != null) {
            parts.add("version = " + quote(version));
        } else if (workspacePath != null) {
            parts.add("workspace = " + quote(workspacePath));
        } else if (!managedDependency) {
            parts.add("version = " + quote(""));
        }
        if (metadata.optional()) {
            parts.add("optional = true");
        }
        if (metadata.publishOnly()) {
            parts.add("publishOnly = true");
        }
        if (!metadata.exclusions().isEmpty()) {
            parts.add("exclusions = [" + exclusions(metadata.exclusions()) + "]");
        }
        toml.append("{ ").append(String.join(", ", parts)).append(" }");
    }

    private static String exclusions(List<DependencyExclusionSpec> exclusions) {
        return exclusions.stream()
                .map(exclusion -> "{ group = "
                        + quote(exclusion.group())
                        + ", artifact = "
                        + quote(exclusion.artifact())
                        + " }")
                .collect(Collectors.joining(", "));
    }

    private static boolean hasDependencyMetadata(Map<String, DependencyMetadata> dependencyMetadata, String section) {
        return dependencyMetadata.values().stream().anyMatch(metadata -> metadata.section().equals(section));
    }

    private static Set<String> sortedCoordinates(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace,
            Map<String, DependencyMetadata> dependencyMetadata,
            String section) {
        TreeSet<String> coordinates = new TreeSet<>();
        coordinates.addAll(versioned.keySet());
        coordinates.addAll(managed);
        coordinates.addAll(workspace.keySet());
        dependencyMetadata.values().stream()
                .filter(metadata -> metadata.section().equals(section))
                .map(DependencyMetadata::coordinate)
                .forEach(coordinates::add);
        return coordinates;
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
