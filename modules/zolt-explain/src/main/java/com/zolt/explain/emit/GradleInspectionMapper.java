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
        GradleProjectInspection primary = primaryProject(result, notes);

        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();

        for (GradleDependencyInspection dependency : primary.dependencies()) {
            mapDependency(dependency, dependencies, runtime, provided, test, notes);
        }
        addCatalogNotes(result.versionCatalogAliases(), notes);
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
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                Set.of(),
                Map.of(),
                runtime,
                Set.of(),
                provided,
                Set.of(),
                Map.of(),
                Set.of(),
                test,
                Set.of(),
                Map.of(),
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

    private static GradleProjectInspection primaryProject(GradleInspectionResult result, List<String> notes) {
        List<GradleProjectInspection> projects = result.projects();
        if (projects.size() > 1 || result.includedProjects().size() > 1) {
            notes.add(
                    "Multi-project Gradle build detected (included projects "
                            + result.includedProjects() + "). This draft maps only the root project;"
                            + " emit each subproject separately and wire workspace deps by hand.");
        }
        return projects.get(0);
    }

    private static void mapDependency(
            GradleDependencyInspection dependency,
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
            case "implementation", "api", "compile" -> dependencies.put(coordinate, version);
            case "runtimeOnly", "runtime" -> runtime.put(coordinate, version);
            case "compileOnly", "compileOnlyApi", "providedCompile" -> provided.put(coordinate, version);
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
