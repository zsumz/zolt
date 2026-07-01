package com.zolt.explain.emit;

import com.zolt.explain.gradle.GradleDependencyInspection;
import com.zolt.explain.gradle.GradleInspectionResult;
import com.zolt.explain.gradle.GradleProjectInspection;
import com.zolt.explain.gradle.GradleRepositoryInspection;
import com.zolt.explain.gradle.GradleVersionCatalogAlias;
import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.NativeSettings;
import com.zolt.project.PackageSettings;
import com.zolt.project.ProjectConfig;
import com.zolt.project.ProjectConfigs;
import com.zolt.project.ProjectMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeMap;

/** Maps a single-project {@link GradleInspectionResult} to a {@link DraftZoltToml}. */
final class GradleInspectionMapper {
    private GradleInspectionMapper() {
    }

    static DraftZoltToml map(GradleInspectionResult result) {
        List<String> notes = new ArrayList<>();
        GradleProjectInspection primary = result.projects().get(0);
        return mapProject(primary, null, result.versionCatalogAliases(), notes);
    }

    /** Maps one subproject, rewriting {@code project(...)} edges to {@code { workspace = ... }}. */
    static DraftZoltToml mapMember(
            GradleProjectInspection project,
            WorkspaceMemberRegistry registry,
            List<GradleVersionCatalogAlias> aliases) {
        return mapProject(project, registry, aliases, new ArrayList<>());
    }

    private static DraftZoltToml mapProject(
            GradleProjectInspection primary,
            WorkspaceMemberRegistry registry,
            List<GradleVersionCatalogAlias> aliases,
            List<String> notes) {
        Map<String, String> apiDependencies = new TreeMap<>();
        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();
        Map<String, String> workspaceApi = new TreeMap<>();
        Map<String, String> workspaceDependencies = new TreeMap<>();
        Map<String, String> workspaceTest = new TreeMap<>();

        for (GradleDependencyInspection dependency : primary.dependencies()) {
            if (mapWorkspaceDependency(
                    dependency, registry, workspaceApi, workspaceDependencies, workspaceTest, notes)) {
                continue;
            }
            mapDependency(dependency, apiDependencies, dependencies, runtime, provided, test, notes);
        }
        addCatalogNotes(aliases, notes);
        addRepositoryNotes(primary.repositories(), notes);

        String group = primary.group().filter(value -> !value.isBlank()).orElse("com.example");
        String version = primary.version().filter(value -> !value.isBlank()).orElse("0.1.0");
        ProjectMetadata metadata = new ProjectMetadata(
                primary.name(),
                version,
                group,
                primary.javaVersion(),
                primary.mainClass().filter(value -> !value.isBlank()));
        boolean groupMissing = primary.group().filter(value -> !value.isBlank()).isEmpty();
        boolean versionMissing = primary.version().filter(value -> !value.isBlank()).isEmpty();
        if (groupMissing && versionMissing) {
            notes.add(
                    "Project group and version are placeholders; the static Gradle audit could not read them."
                            + " Set `group` and `version` to your real coordinates.");
        } else if (groupMissing) {
            notes.add(
                    "Project group is a placeholder; the static Gradle audit could not read it."
                            + " Set `group` to your real coordinate.");
        } else if (versionMissing) {
            notes.add(
                    "Project version is a placeholder; the static Gradle audit could not read it."
                            + " Set `version` to your real coordinate.");
        }

        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                Map.of(),
                apiDependencies,
                Set.of(),
                workspaceApi,
                dependencies,
                Set.of(),
                workspaceDependencies,
                runtime,
                Set.of(),
                provided,
                Set.of(),
                Map.of(),
                Set.of(),
                test,
                Set.of(),
                workspaceTest,
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                BuildSettings.defaults(),
                NativeSettings.defaults(),
                CompilerSettings.defaults(),
                PackageSettings.defaults());
        return new DraftZoltToml(config, notes);
    }

    /**
     * Rewrites a {@code project(":lib")} edge to {@code { workspace = "<path>" }}. Returns true when
     * the dependency was a project edge (recorded or noted), false otherwise.
     */
    private static boolean mapWorkspaceDependency(
            GradleDependencyInspection dependency,
            WorkspaceMemberRegistry registry,
            Map<String, String> workspaceApi,
            Map<String, String> workspaceDependencies,
            Map<String, String> workspaceTest,
            List<String> notes) {
        String projectPath = projectPath(dependency.notation());
        if (projectPath == null) {
            return false;
        }
        String memberPath = registry == null ? null : registry.pathFor(projectPath);
        if (memberPath == null) {
            notes.add(
                    "Gradle dependency `project(\"" + dependency.notation() + "\")` in `"
                            + dependency.configuration() + "` targets a project outside this workspace;"
                            + " wire it by hand.");
            return true;
        }
        switch (dependency.configuration()) {
            case "api", "compileOnlyApi" -> workspaceApi.put(memberPath, memberPath);
            case "implementation", "compile" -> workspaceDependencies.put(memberPath, memberPath);
            case "testImplementation", "testRuntimeOnly", "testCompile", "testCompileOnly" ->
                    workspaceTest.put(memberPath, memberPath);
            default -> notes.add(
                    "Gradle dependency `project(\"" + dependency.notation() + "\")` in `"
                            + dependency.configuration() + "` maps to sibling module `" + memberPath
                            + "`, but that configuration has no direct workspace section; wire it by hand.");
        }
        return true;
    }

    /**
     * The Gradle project path a {@code project(":a:b")} notation refers to, normalized to a workspace
     * directory path ({@code a/b}); {@code null} when the notation is not a project reference.
     */
    private static String projectPath(String notation) {
        if (notation == null || !notation.startsWith(":")) {
            return null;
        }
        String path = notation.replaceFirst("^:+", "").replace(':', '/').strip();
        return path.isBlank() ? null : path;
    }

    private static void mapDependency(
            GradleDependencyInspection dependency,
            Map<String, String> apiDependencies,
            Map<String, String> dependencies,
            Map<String, String> runtime,
            Map<String, String> provided,
            Map<String, String> test,
            List<String> notes) {
        String resolved = dependency.resolvedCoordinate();
        if (resolved == null || resolved.isBlank()) {
            if (dependency.versionCatalogAlias() != null && !dependency.versionCatalogAlias().isBlank()) {
                notes.add(
                        "Gradle dependency in `" + dependency.configuration() + "` uses version-catalog"
                                + " alias `" + dependency.versionCatalogAlias() + "` with no resolved"
                                + " coordinate; look it up in libs.versions.toml and add it by hand.");
            } else {
                notes.add(
                        "Gradle dependency notation `" + dependency.notation() + "` in `"
                                + dependency.configuration() + "` could not be resolved to a coordinate;"
                                + " add it by hand after confirming the group:name:version.");
            }
            return;
        }
        String coordinate = coordinateOf(resolved);
        String version = versionOf(resolved);
        if (version == null) {
            notes.add(
                    "Gradle dependency `" + coordinate + "` in `" + dependency.configuration()
                            + "` has no version in its resolved coordinate; add one before resolving.");
            return;
        }
        switch (dependency.configuration()) {
            case "api", "compileOnlyApi" -> apiDependencies.put(coordinate, version);
            case "implementation", "compile" -> dependencies.put(coordinate, version);
            case "runtimeOnly", "runtime" -> runtime.put(coordinate, version);
            case "compileOnly", "providedCompile" -> provided.put(coordinate, version);
            case "testImplementation", "testRuntimeOnly", "testCompile", "testCompileOnly" ->
                    test.put(coordinate, version);
            default -> notes.add(
                    "Gradle configuration `" + dependency.configuration() + "` for `" + coordinate
                            + "` has no direct Zolt section; place it manually after review.");
        }
    }

    private static void addCatalogNotes(List<GradleVersionCatalogAlias> aliases, List<String> notes) {
        for (GradleVersionCatalogAlias alias : aliases) {
            if (alias.coordinate() == null || alias.coordinate().isBlank()) {
                notes.add(
                        "Version-catalog alias `" + alias.alias() + "` has no coordinate in the audit;"
                                + " resolve it from libs.versions.toml before use.");
            }
        }
    }

    private static void addRepositoryNotes(List<GradleRepositoryInspection> repositories, List<String> notes) {
        for (GradleRepositoryInspection repository : repositories) {
            if (repository.url() == null || repository.url().isBlank()) {
                continue;
            }
            if (repository.url().contains("repo.maven.apache.org") || "mavenCentral".equals(repository.kind())) {
                continue;
            }
            notes.add(
                    "Custom Gradle repository `" + repository.url() + "` (" + repository.kind()
                            + ") was declared; Zolt defaults to Maven Central only. Add it under"
                            + " [repositories] if your build needs it.");
        }
    }

    private static String coordinateOf(String resolved) {
        String[] parts = resolved.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return resolved;
    }

    private static String versionOf(String resolved) {
        String[] parts = resolved.split(":");
        if (parts.length >= 3 && !parts[2].isBlank()) {
            return parts[2];
        }
        return null;
    }
}
