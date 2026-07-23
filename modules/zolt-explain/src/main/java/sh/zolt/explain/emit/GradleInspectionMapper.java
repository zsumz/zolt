package sh.zolt.explain.emit;

import sh.zolt.explain.gradle.GradleDependencyInspection;
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
import java.util.TreeMap;
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
        Map<String, String> platforms = new TreeMap<>();
        Map<String, String> apiDependencies = new TreeMap<>();
        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();
        Map<String, String> workspaceApi = new TreeMap<>();
        Map<String, String> workspaceDependencies = new TreeMap<>();
        Map<String, String> workspaceTest = new TreeMap<>();
        ManagedSections managed = new ManagedSections();
        Set<String> commentedProjectKeys = new TreeSet<>();

        // A platform(...) import is scope-agnostic in Zolt: route it to [platforms] first, then let the
        // presence of a platform decide whether a version-less dependency is emitted as platform-managed.
        for (GradleDependencyInspection dependency : primary.dependencies()) {
            if (dependency.isPlatform()) {
                mapPlatform(dependency, platforms, notes);
            }
        }
        boolean platformAvailable = !platforms.isEmpty();
        for (GradleDependencyInspection dependency : primary.dependencies()) {
            if (dependency.isPlatform()) {
                continue;
            }
            if (mapWorkspaceDependency(
                    dependency, registry, workspaceApi, workspaceDependencies, workspaceTest, notes)) {
                continue;
            }
            mapDependency(
                    dependency, platformAvailable, apiDependencies, dependencies, runtime, provided, test,
                    managed, notes);
        }
        addCatalogNotes(aliases, notes);
        addRepositoryNotes(primary.repositories(), notes);

        String group = emittedGroup(primary);
        String version = primary.version().filter(value -> !value.isBlank()).orElse("0.1.0");
        String javaVersion = javaVersion(primary.javaVersion(), notes, commentedProjectKeys);
        ProjectMetadata metadata = new ProjectMetadata(
                primary.name(),
                version,
                group,
                javaVersion,
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
        BuildSettings defaultBuild = BuildSettings.defaults();

        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                platforms,
                apiDependencies,
                managed.api(),
                workspaceApi,
                dependencies,
                managed.dependencies(),
                workspaceDependencies,
                runtime,
                managed.runtime(),
                provided,
                managed.provided(),
                Map.of(),
                Set.of(),
                test,
                managed.test(),
                workspaceTest,
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

    private static String emittedGroup(GradleProjectInspection project) {
        return project.group().filter(value -> !value.isBlank()).orElse("com.example");
    }

    private static String javaVersion(
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
        WorkspaceMemberRegistry.Member member = registry == null ? null : registry.memberFor(projectPath);
        if (member == null) {
            notes.add(
                    "Gradle dependency `project(\"" + dependency.notation() + "\")` in `"
                            + dependency.configuration() + "` targets a project outside this workspace;"
                            + " wire it by hand.");
            return true;
        }
        String coordinate = member.coordinate();
        String memberPath = member.path();
        switch (dependency.configuration()) {
            case "api", "compileOnlyApi" -> workspaceApi.put(coordinate, memberPath);
            case "implementation", "compile" -> workspaceDependencies.put(coordinate, memberPath);
            case "testImplementation", "testRuntimeOnly", "testCompile", "testCompileOnly" ->
                    workspaceTest.put(coordinate, memberPath);
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

    /**
     * Routes a {@code platform(...)} / {@code enforcedPlatform(...)} import to {@code [platforms]}.
     * {@code enforcedPlatform} maps like a platform plus a review note, because Gradle's enforced
     * semantics (the BOM's versions override transitive versions) are only approximated by a Zolt
     * platform; the honest analog for a hard pin is a {@code [dependencyConstraints]} strict entry,
     * which this draft points at rather than auto-generates.
     */
    private static void mapPlatform(
            GradleDependencyInspection dependency, Map<String, String> platforms, List<String> notes) {
        String resolved = dependency.resolvedCoordinate();
        if (resolved == null || resolved.isBlank()) {
            if (dependency.versionCatalogAlias() != null && !dependency.versionCatalogAlias().isBlank()) {
                notes.add(
                        "Gradle platform import in `" + dependency.configuration() + "` uses version-catalog"
                                + " alias `" + dependency.versionCatalogAlias() + "` with no resolved coordinate;"
                                + " look it up in libs.versions.toml and add it under [platforms] by hand.");
            } else {
                notes.add(
                        "Gradle platform import `" + dependency.notation() + "` in `"
                                + dependency.configuration() + "` could not be resolved to a coordinate;"
                                + " add it under [platforms] by hand after confirming the group:name:version.");
            }
            return;
        }
        String coordinate = coordinateOf(resolved);
        String version = versionOf(resolved);
        if (version == null) {
            notes.add(
                    "Gradle platform import `" + coordinate + "` in `" + dependency.configuration()
                            + "` has no version in its resolved coordinate; add one under [platforms] before"
                            + " resolving.");
            return;
        }
        platforms.put(coordinate, version);
        if (dependency.platformKind() == GradleDependencyInspection.PlatformKind.ENFORCED_PLATFORM) {
            notes.add(
                    "Gradle `enforcedPlatform(" + coordinate + ")` was mapped to [platforms]. Gradle's"
                            + " enforced semantics (forcing the BOM's managed versions over transitive"
                            + " versions) are only approximated; if you must hard-pin, add a"
                            + " [dependencyConstraints] entry with kind = \"strict\" per coordinate. This draft"
                            + " does not auto-generate those constraints.");
        }
    }

    private static void mapDependency(
            GradleDependencyInspection dependency,
            boolean platformAvailable,
            Map<String, String> apiDependencies,
            Map<String, String> dependencies,
            Map<String, String> runtime,
            Map<String, String> provided,
            Map<String, String> test,
            ManagedSections managed,
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
            if (platformAvailable && mapManagedDependency(dependency.configuration(), coordinate, managed)) {
                notes.add(
                        "Gradle dependency `" + coordinate + "` in `" + dependency.configuration()
                                + "` has no version and is emitted as platform-managed `{}`; verify a declared"
                                + " platform manages this coordinate before resolving.");
                return;
            }
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

    /**
     * Adds a version-less coordinate to the platform-managed set for its configuration (rendered as
     * {@code coordinate = {}}). Returns false for configurations without a managed section so the caller
     * falls back to the hard "add a version" review item.
     */
    private static boolean mapManagedDependency(
            String configuration, String coordinate, ManagedSections managed) {
        switch (configuration) {
            case "api", "compileOnlyApi" -> managed.api().add(coordinate);
            case "implementation", "compile" -> managed.dependencies().add(coordinate);
            case "runtimeOnly", "runtime" -> managed.runtime().add(coordinate);
            case "compileOnly", "providedCompile" -> managed.provided().add(coordinate);
            case "testImplementation", "testRuntimeOnly", "testCompile", "testCompileOnly" ->
                    managed.test().add(coordinate);
            default -> {
                return false;
            }
        }
        return true;
    }

    /** The platform-managed ({@code {}}) coordinate sets, one per dependency section. */
    private static final class ManagedSections {
        private final Set<String> api = new TreeSet<>();
        private final Set<String> dependencies = new TreeSet<>();
        private final Set<String> runtime = new TreeSet<>();
        private final Set<String> provided = new TreeSet<>();
        private final Set<String> test = new TreeSet<>();

        Set<String> api() {
            return api;
        }

        Set<String> dependencies() {
            return dependencies;
        }

        Set<String> runtime() {
            return runtime;
        }

        Set<String> provided() {
            return provided;
        }

        Set<String> test() {
            return test;
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

    private static String path(String path) {
        return path.replace('\\', '/');
    }
}
