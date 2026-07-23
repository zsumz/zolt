package sh.zolt.explain.emit;

import sh.zolt.explain.gradle.GradleDependencyInspection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

/**
 * Routes a Gradle project's dependencies into the draft's platform, versioned, platform-managed, and
 * workspace-edge sections. A {@code platform(...)} import lands in {@code [platforms]}; a version-less
 * dependency in a build file that imports a platform becomes platform-managed {@code {}}; a
 * {@code project(":lib")} edge becomes a {@code { workspace = ... }} member reference. Mirrors
 * {@link MavenDependencySectionMapper}.
 */
final class GradleDependencySectionMapper {
    private final Map<String, String> platforms = new TreeMap<>();
    private final Map<String, String> apiDependencies = new TreeMap<>();
    private final Map<String, String> dependencies = new TreeMap<>();
    private final Map<String, String> runtime = new TreeMap<>();
    private final Map<String, String> provided = new TreeMap<>();
    private final Map<String, String> test = new TreeMap<>();
    private final Map<String, String> workspaceApi = new TreeMap<>();
    private final Map<String, String> workspaceDependencies = new TreeMap<>();
    private final Map<String, String> workspaceTest = new TreeMap<>();
    private final Set<String> managedApi = new TreeSet<>();
    private final Set<String> managedDependencies = new TreeSet<>();
    private final Set<String> managedRuntime = new TreeSet<>();
    private final Set<String> managedProvided = new TreeSet<>();
    private final Set<String> managedTest = new TreeSet<>();
    private final WorkspaceMemberRegistry registry;
    private final List<String> notes;

    GradleDependencySectionMapper(WorkspaceMemberRegistry registry, List<String> notes) {
        this.registry = registry;
        this.notes = notes;
    }

    void map(List<GradleDependencyInspection> declared) {
        // A platform(...) import is scope-agnostic in Zolt: route it to [platforms] first, then let the
        // presence of a platform decide whether a version-less dependency is emitted as platform-managed.
        for (GradleDependencyInspection dependency : declared) {
            if (dependency.isPlatform()) {
                mapPlatform(dependency);
            }
        }
        boolean platformAvailable = !platforms.isEmpty();
        for (GradleDependencyInspection dependency : declared) {
            if (dependency.isPlatform()) {
                continue;
            }
            if (mapWorkspaceDependency(dependency)) {
                continue;
            }
            mapDependency(dependency, platformAvailable);
        }
    }

    Map<String, String> platforms() {
        return platforms;
    }

    Map<String, String> apiDependencies() {
        return apiDependencies;
    }

    Map<String, String> dependencies() {
        return dependencies;
    }

    Map<String, String> runtime() {
        return runtime;
    }

    Map<String, String> provided() {
        return provided;
    }

    Map<String, String> test() {
        return test;
    }

    Map<String, String> workspaceApi() {
        return workspaceApi;
    }

    Map<String, String> workspaceDependencies() {
        return workspaceDependencies;
    }

    Map<String, String> workspaceTest() {
        return workspaceTest;
    }

    Set<String> managedApi() {
        return managedApi;
    }

    Set<String> managedDependencies() {
        return managedDependencies;
    }

    Set<String> managedRuntime() {
        return managedRuntime;
    }

    Set<String> managedProvided() {
        return managedProvided;
    }

    Set<String> managedTest() {
        return managedTest;
    }

    /**
     * Routes a {@code platform(...)} / {@code enforcedPlatform(...)} import to {@code [platforms]}.
     * {@code enforcedPlatform} maps like a platform plus a review note, because Gradle's enforced
     * semantics (the BOM's versions override transitive versions) are only approximated by a Zolt
     * platform; the honest analog for a hard pin is a {@code [dependencyConstraints]} strict entry,
     * which this draft points at rather than auto-generates.
     */
    private void mapPlatform(GradleDependencyInspection dependency) {
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
        String coordinate = GradleInspectionMapper.coordinateOf(resolved);
        String version = GradleInspectionMapper.versionOf(resolved);
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

    /**
     * Rewrites a {@code project(":lib")} edge to {@code { workspace = "<path>" }}. Returns true when
     * the dependency was a project edge (recorded or noted), false otherwise.
     */
    private boolean mapWorkspaceDependency(GradleDependencyInspection dependency) {
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

    private void mapDependency(GradleDependencyInspection dependency, boolean platformAvailable) {
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
        String coordinate = GradleInspectionMapper.coordinateOf(resolved);
        String version = GradleInspectionMapper.versionOf(resolved);
        if (version == null) {
            if (platformAvailable && mapManagedDependency(dependency.configuration(), coordinate)) {
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
    private boolean mapManagedDependency(String configuration, String coordinate) {
        switch (configuration) {
            case "api", "compileOnlyApi" -> managedApi.add(coordinate);
            case "implementation", "compile" -> managedDependencies.add(coordinate);
            case "runtimeOnly", "runtime" -> managedRuntime.add(coordinate);
            case "compileOnly", "providedCompile" -> managedProvided.add(coordinate);
            case "testImplementation", "testRuntimeOnly", "testCompile", "testCompileOnly" ->
                    managedTest.add(coordinate);
            default -> {
                return false;
            }
        }
        return true;
    }
}
