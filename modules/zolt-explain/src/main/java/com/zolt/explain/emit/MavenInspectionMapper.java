package com.zolt.explain.emit;

import com.zolt.explain.maven.MavenAnnotationProcessorInspection;
import com.zolt.explain.maven.MavenDependencyInspection;
import com.zolt.explain.maven.MavenInspectionResult;
import com.zolt.explain.maven.MavenProjectInspection;
import com.zolt.explain.maven.MavenRepositoryInspection;
import com.zolt.project.BuildSettings;
import com.zolt.project.CompilerSettings;
import com.zolt.project.DependencyConstraint;
import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencyPolicySettings;
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
import java.util.TreeSet;

/** Maps a single-project {@link MavenInspectionResult} to a {@link DraftZoltToml}. */
final class MavenInspectionMapper {
    private static final String PLACEHOLDER_GROUP = "com.example";
    private static final String PLACEHOLDER_VERSION = "0.1.0";

    private MavenInspectionMapper() {
    }

    static DraftZoltToml map(MavenInspectionResult result) {
        List<String> notes = new ArrayList<>();
        MavenProjectInspection primary = result.projects().get(0);
        return mapProject(primary, null, notes);
    }

    /** Maps one reactor member, rewriting sibling deps to {@code { workspace = ... }} via the registry. */
    static DraftZoltToml mapMember(MavenProjectInspection project, WorkspaceMemberRegistry registry) {
        return mapProject(project, registry, new ArrayList<>());
    }

    private static DraftZoltToml mapProject(
            MavenProjectInspection primary,
            WorkspaceMemberRegistry registry,
            List<String> notes) {
        Map<String, String> dependencies = new TreeMap<>();
        Map<String, String> runtime = new TreeMap<>();
        Map<String, String> provided = new TreeMap<>();
        Map<String, String> test = new TreeMap<>();
        Set<String> managedDependencies = new TreeSet<>();
        Set<String> managedRuntime = new TreeSet<>();
        Set<String> managedProvided = new TreeSet<>();
        Set<String> managedTest = new TreeSet<>();
        Map<String, String> workspaceDependencies = new TreeMap<>();
        Map<String, String> workspaceTest = new TreeMap<>();
        Map<String, String> platforms = new TreeMap<>();
        Map<String, String> annotationProcessors = new TreeMap<>();
        Map<String, DependencyMetadata> dependencyMetadata = new TreeMap<>();

        for (MavenDependencyInspection bom : primary.importedBoms()) {
            mapPlatform(bom, platforms, notes);
        }
        Map<String, DependencyConstraint> constraints =
                MavenDependencyConstraintMapper.map(
                        primary.dependencyManagement(),
                        primary.dependencies(),
                        notes);
        MavenDependencySectionMapper dependencyMapper = new MavenDependencySectionMapper(
                registry,
                new MavenDependencySectionMapper.VersionedSections(
                        dependencies,
                        runtime,
                        provided,
                        test,
                        workspaceDependencies,
                        workspaceTest),
                new MavenDependencySectionMapper.ManagedSections(
                        managedDependencies,
                        managedRuntime,
                        managedProvided,
                        managedTest),
                !platforms.isEmpty(),
                dependencyMetadata,
                notes);
        for (MavenDependencyInspection dependency : primary.dependencies()) {
            dependencyMapper.map(dependency);
        }
        for (MavenAnnotationProcessorInspection processor : primary.annotationProcessors()) {
            mapAnnotationProcessor(processor, annotationProcessors, notes);
        }
        addRepositoryNotes(primary.repositories(), notes);
        addProfileNotes(primary, notes);

        String group = group(primary, notes);
        String version = version(primary, notes);
        ProjectMetadata metadata = new ProjectMetadata(
                primary.artifactId(),
                version,
                group,
                JavaVersionNotation.normalizeLegacyFeature(primary.javaVersion()),
                Optional.empty());

        ProjectConfig config = ProjectConfigs.withAllDependencySections(
                metadata,
                ProjectConfig.defaultRepositories(),
                platforms,
                Map.of(),
                Set.of(),
                Map.of(),
                dependencies,
                managedDependencies,
                workspaceDependencies,
                runtime,
                managedRuntime,
                provided,
                managedProvided,
                Map.of(),
                Set.of(),
                test,
                managedTest,
                workspaceTest,
                annotationProcessors,
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
        if (!constraints.isEmpty()) {
            config = config.withDependencyPolicy(new DependencyPolicySettings(List.of(), constraints));
        }
        return new DraftZoltToml(config, notes);
    }

    private static void mapAnnotationProcessor(
            MavenAnnotationProcessorInspection processor,
            Map<String, String> annotationProcessors,
            List<String> notes) {
        String coordinate = coordinateOf(processor.coordinate());
        if (processor.version().isBlank()) {
            notes.add(
                    "Annotation processor `" + coordinate + "` has no static version; add it under"
                            + " [annotationProcessors] before resolving.");
            return;
        }
        if (processor.version().contains("${")) {
            notes.add(
                    "Annotation processor `" + coordinate + "` uses version `" + processor.version()
                            + "`, which references a property the static audit could not resolve. Replace it"
                            + " with a fixed version under [annotationProcessors] before resolving.");
            return;
        }
        annotationProcessors.put(coordinate, processor.version());
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
