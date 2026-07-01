package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenDependencyExclusion;
import com.zolt.explain.maven.MavenDependencyInspection;
import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenProjectInspection;
import com.zolt.explain.maven.MavenRepositoryInspection;
import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyExclusionSpec;
import com.zolt.project.DependencyMetadata;
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

/** Maps a single-project {@link MavenInspectionResult} to a {@link DraftZoltToml}. */
final class MavenInspectionMapper {
    private static final String PLACEHOLDER_GROUP = "com.example";
    private static final String PLACEHOLDER_VERSION = "0.1.0";

    private MavenInspectionMapper() {
    }

    static DraftZoltToml map(MavenInspectionResult result) {
        List<String> notes = new ArrayList<>();
        MavenProjectInspection primary = primaryProject(result, notes);

        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();
        Map<String, String> platforms = new TreeMap<>();
        Map<String, DependencyMetadata> dependencyMetadata = new TreeMap<>();

        for (MavenDependencyInspection dependency : primary.dependencies()) {
            mapDependency(dependency, dependencies, runtime, provided, test, dependencyMetadata, notes);
        }
        for (MavenDependencyInspection bom : primary.importedBoms()) {
            mapPlatform(bom, platforms, notes);
        }
        addRepositoryNotes(primary.repositories(), notes);
        addProfileNotes(primary, notes);

        String group = group(primary, notes);
        String version = version(primary, notes);
        ProjectMetadata metadata = new ProjectMetadata(
                primary.name(),
                version,
                group,
                primary.javaVersion(),
                Optional.empty());

        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                platforms,
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
        if (!dependencyMetadata.isEmpty()) {
            config = config.withDependencyMetadata(dependencyMetadata);
        }
        return new DraftZoltToml(config, notes);
    }

    private static MavenProjectInspection primaryProject(MavenInspectionResult result, List<String> notes) {
        List<MavenProjectInspection> projects = result.projects();
        MavenProjectInspection root = projects.get(0);
        if (projects.size() > 1 || "pom".equals(root.packaging()) || !root.modules().isEmpty()) {
            notes.add(
                    "Multi-module reactor detected (root packaging `" + root.packaging() + "`, modules "
                            + root.modules() + "). This draft maps only the root pom; emit each module"
                            + " separately and wire workspace deps by hand.");
        }
        return root;
    }

    private static void mapDependency(
            MavenDependencyInspection dependency,
            Map<String, String> dependencies,
            Map<String, String> runtime,
            Map<String, String> provided,
            Map<String, String> test,
            Map<String, DependencyMetadata> dependencyMetadata,
            List<String> notes) {
        String coordinate = coordinateOf(dependency.coordinate());
        String version = dependency.version();
        if (version.isBlank()) {
            notes.add(
                    "Dependency `" + coordinate + "` (scope " + dependency.scope() + ") has no static"
                            + " version; it is likely managed by a BOM. Add a version or platform entry"
                            + " before resolving.");
            return;
        }
        if (version.contains("${")) {
            notes.add(
                    "Dependency `" + coordinate + "` (scope " + dependency.scope() + ") uses version `"
                            + version + "`, which references a property the static audit could not"
                            + " resolve. Replace it with a fixed version before resolving.");
            return;
        }
        String section = switch (dependency.scope()) {
            case "compile" -> {
                dependencies.put(coordinate, version);
                yield "dependencies";
            }
            case "runtime" -> {
                runtime.put(coordinate, version);
                yield "runtime.dependencies";
            }
            case "provided" -> {
                provided.put(coordinate, version);
                yield "provided.dependencies";
            }
            case "test" -> {
                test.put(coordinate, version);
                yield "test.dependencies";
            }
            default -> {
                notes.add(
                        "Dependency `" + coordinate + "` uses Maven scope `" + dependency.scope()
                                + "`, which has no direct Zolt section; place it manually after review.");
                yield null;
            }
        };
        if (section != null) {
            recordExclusions(section, coordinate, dependency, dependencyMetadata);
        }
    }

    private static void recordExclusions(
            String section,
            String coordinate,
            MavenDependencyInspection dependency,
            Map<String, DependencyMetadata> dependencyMetadata) {
        if (dependency.exclusions().isEmpty()) {
            return;
        }
        List<DependencyExclusionSpec> specs = new ArrayList<>();
        for (MavenDependencyExclusion exclusion : dependency.exclusions()) {
            specs.add(new DependencyExclusionSpec(exclusion.groupId(), exclusion.artifactId()));
        }
        DependencyMetadata metadata = new DependencyMetadata(
                section,
                coordinate,
                null,
                dependency.managed(),
                null,
                false,
                false,
                specs);
        dependencyMetadata.put(DependencyMetadata.key(section, coordinate), metadata);
    }

    private static void mapPlatform(
            MavenDependencyInspection bom,
            Map<String, String> platforms,
            List<String> notes) {
        String coordinate = coordinateOf(bom.coordinate());
        if (bom.version().isBlank()) {
            notes.add(
                    "Imported BOM `" + coordinate + "` has no static version; add a version under"
                            + " [platforms] before resolving.");
            return;
        }
        if (bom.version().contains("${")) {
            notes.add(
                    "Imported BOM `" + coordinate + "` uses version `" + bom.version() + "`, which"
                            + " references a property the static audit could not resolve. Replace it with a"
                            + " fixed version under [platforms] before resolving.");
            return;
        }
        platforms.put(coordinate, bom.version());
    }

    private static void addRepositoryNotes(List<MavenRepositoryInspection> repositories, List<String> notes) {
        for (MavenRepositoryInspection repository : repositories) {
            notes.add(
                    "Custom Maven repository `" + repository.url() + "` was declared; Zolt defaults to"
                            + " Maven Central only. Add it under [repositories] if your build needs it.");
        }
    }

    private static void addProfileNotes(MavenProjectInspection project, List<String> notes) {
        if (!project.profiles().isEmpty()) {
            notes.add(
                    "Maven profiles were detected and are not translated; Zolt has no profile concept."
                            + " Fold any required profile config into this zolt.toml by hand.");
        }
    }

    private static String group(MavenProjectInspection project, List<String> notes) {
        String groupId = project.groupId();
        if (groupId != null && !groupId.isBlank()) {
            return groupId;
        }
        notes.add(
                "Project group could not be read from the static audit; `group` is a placeholder."
                        + " Set it to your real Maven groupId.");
        return PLACEHOLDER_GROUP;
    }

    private static String version(MavenProjectInspection project, List<String> notes) {
        String version = project.version();
        if (version != null && !version.isBlank()) {
            return version;
        }
        notes.add(
                "Project version could not be read from the static audit; `version` is a placeholder."
                        + " Set it to your real release before publishing.");
        return PLACEHOLDER_VERSION;
    }

    private static String coordinateOf(String coordinate) {
        String[] parts = coordinate.split(":");
        if (parts.length >= 2) {
            return parts[0] + ":" + parts[1];
        }
        return coordinate;
    }
}
