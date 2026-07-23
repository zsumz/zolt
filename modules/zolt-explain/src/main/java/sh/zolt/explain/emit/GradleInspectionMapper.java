package sh.zolt.explain.emit;

import sh.zolt.explain.gradle.GradleInspectionResult;
import sh.zolt.explain.gradle.GradleProjectInspection;
import sh.zolt.explain.gradle.GradleRepositoryInspection;
import sh.zolt.explain.gradle.GradleVersionCatalogAlias;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/** Maps a single-project {@link GradleInspectionResult} to a {@link DraftZoltToml}. */
final class GradleInspectionMapper {
    private GradleInspectionMapper() {
    }

    static DraftZoltToml map(GradleInspectionResult result) {
        List<String> notes = skippedIncludedProjectNotes(result);
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

    static List<String> skippedIncludedProjectNotes(GradleInspectionResult result) {
        List<String> inspectedMembers = result.projects().stream()
                .map(project -> path(project.path().toString()))
                .filter(path -> !".".equals(path))
                .toList();
        List<String> skippedMembers = result.includedProjects().stream()
                .filter(path -> !inspectedMembers.contains(path))
                .toList();
        if (skippedMembers.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(List.of(
                "Gradle settings included " + skippedMembers.size()
                        + " project(s) that the static audit could not map to a build file: "
                        + String.join(", ", skippedMembers)
                        + ". These members are not emitted in this draft; review the explain signals before use."));
    }

    static String emittedCoordinate(GradleProjectInspection project) {
        return emittedGroup(project) + ":" + project.name();
    }

    private static DraftZoltToml mapProject(
            GradleProjectInspection primary,
            WorkspaceMemberRegistry registry,
            List<GradleVersionCatalogAlias> aliases,
            List<String> notes) {
        // A standalone java-platform project drafts a [bom] member. Workspace members keep the existing
        // platform/dependency routing so multi-project emit stays stable.
        if (registry == null && GradleBomDraftMapper.isBom(primary)) {
            return GradleBomDraftMapper.map(primary, notes);
        }
        GradleDependencySectionMapper sections = new GradleDependencySectionMapper(registry, notes);
        sections.map(primary.dependencies());
        addCatalogNotes(aliases, notes);
        addRepositoryNotes(primary.repositories(), notes);

        Set<String> commentedProjectKeys = new TreeSet<>();
        ProjectMetadata metadata = new ProjectMetadata(
                primary.name(),
                emittedVersion(primary),
                emittedGroup(primary),
                javaVersion(primary.javaVersion(), notes, commentedProjectKeys),
                primary.mainClass().filter(value -> !value.isBlank()));
        addCoordinatePlaceholderNotes(primary, notes);
        BuildSettings defaultBuild = BuildSettings.defaults();

        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                sections.platforms(),
                sections.apiDependencies(),
                sections.managedApi(),
                sections.workspaceApi(),
                sections.dependencies(),
                sections.managedDependencies(),
                sections.workspaceDependencies(),
                sections.runtime(),
                sections.managedRuntime(),
                sections.provided(),
                sections.managedProvided(),
                Map.of(),
                Set.of(),
                sections.test(),
                sections.managedTest(),
                sections.workspaceTest(),
                Map.of(),
                Set.of(),
                Map.of(),
                Set.of(),
                InspectionBuildSettingsMapper.fromRoots(
                        primary.sourceRoots(),
                        primary.testSourceRoots(),
                        primary.groovyTestSourceRoots(),
                        defaultBuild.resourceRoots(),
                        defaultBuild.testResourceRoots(),
                        notes),
                NativeSettings.defaults(),
                CompilerSettings.defaults(),
                PackageSettings.defaults());
        return new DraftZoltToml(config, notes, List.copyOf(commentedProjectKeys));
    }

    static String emittedGroup(GradleProjectInspection project) {
        return project.group().filter(value -> !value.isBlank()).orElse("com.example");
    }

    static String emittedVersion(GradleProjectInspection project) {
        return project.version().filter(value -> !value.isBlank()).orElse("0.1.0");
    }

    /** Adds the group/version placeholder review notes when the static audit could not read them. */
    static void addCoordinatePlaceholderNotes(GradleProjectInspection project, List<String> notes) {
        boolean groupMissing = project.group().filter(value -> !value.isBlank()).isEmpty();
        boolean versionMissing = project.version().filter(value -> !value.isBlank()).isEmpty();
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
    }

    static String javaVersion(
            String inspected,
            List<String> notes,
            Set<String> commentedProjectKeys) {
        Optional<String> liveFeature = JavaVersionNotation.liveFeature(inspected);
        if (liveFeature.isPresent()) {
            return liveFeature.get();
        }
        String reviewValue = JavaVersionNotation.reviewValue(inspected);
        commentedProjectKeys.add("java");
        notes.add(
                "Project Java version could not be determined from the static audit (`" + reviewValue
                        + "`); uncomment `[project].java` and set it to the Java feature version before"
                        + " resolving or building.");
        return reviewValue;
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

    static String coordinateOf(String resolved) {
        String[] parts = resolved.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return resolved;
    }

    static String versionOf(String resolved) {
        String[] parts = resolved.split(":");
        if (parts.length >= 3 && !parts[2].isBlank()) {
            return parts[2];
        }
        return null;
    }

    private static String path(String path) {
        return path.replace('\\', '/');
    }
}
