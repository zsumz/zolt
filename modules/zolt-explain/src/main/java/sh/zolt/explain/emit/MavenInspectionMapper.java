package sh.zolt.explain.emit;

import sh.zolt.explain.maven.MavenAnnotationProcessorInspection;
import sh.zolt.explain.maven.MavenDependencyInspection;
import sh.zolt.explain.maven.MavenInspectionResult;
import sh.zolt.explain.maven.MavenPlatformApiHostCandidate;
import sh.zolt.explain.maven.MavenProjectInspection;
import sh.zolt.explain.maven.MavenRepositoryInspection;
import sh.zolt.project.BuildSettings;
import sh.zolt.project.CompilerSettings;
import sh.zolt.project.DependencyConstraint;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DependencyPolicySettings;
import sh.zolt.project.NativeSettings;
import sh.zolt.project.PackageSettings;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.ProjectConfigs;
import sh.zolt.project.ProjectMetadata;
import sh.zolt.project.VersionPolicy;
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
        return mapProject(primary, null, Map.of(), notes);
    }

    /** Maps one reactor member, rewriting sibling deps to {@code { workspace = ... }} via the registry. */
    static DraftZoltToml mapMember(
            MavenProjectInspection project,
            WorkspaceMemberRegistry registry,
            Map<String, MavenProjectInspection> reactorProjects) {
        return mapProject(project, registry, reactorProjects, new ArrayList<>());
    }

    private static DraftZoltToml mapProject(
            MavenProjectInspection primary,
            WorkspaceMemberRegistry registry,
            Map<String, MavenProjectInspection> reactorProjects,
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
        Set<String> commentedProjectKeys = new TreeSet<>();

        MavenPlatformMapping platformMapping =
                MavenPlatformMapper.map(primary.importedBoms(), registry, reactorProjects, notes);
        platforms.putAll(platformMapping.platforms());
        List<MavenDependencyInspection> managedForConstraints = new ArrayList<>(primary.dependencyManagement());
        managedForConstraints.addAll(platformMapping.managedDependencies());
        Map<String, DependencyConstraint> constraints =
                MavenDependencyConstraintMapper.map(
                        managedForConstraints,
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
                platformMapping.managedPins(),
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
        String javaVersion = javaVersion(primary.javaVersion(), notes, commentedProjectKeys);
        addTestJavaVersionNote(primary, notes);
        boolean suggestPlatformApiHost = MavenPlatformApiHostCandidate.applies(primary);
        if (suggestPlatformApiHost) {
            notes.add(platformApiHostNote(primary.javaVersion()));
        }
        ProjectMetadata metadata = new ProjectMetadata(
                primary.artifactId(),
                version,
                group,
                javaVersion,
                Optional.empty());

        BuildSettings buildSettings = InspectionBuildSettingsMapper.fromRoots(
                primary.sourceRoots(),
                primary.testSourceRoots(),
                primary.resourceRoots(),
                primary.testResourceRoots(),
                notes);
        MavenExecStepDrafter.Drafted drafted = MavenExecStepDrafter.draft(primary.plugins(), notes);
        if (!drafted.isEmpty()) {
            buildSettings = buildSettings.withGeneratedSources(drafted.mainSteps(), drafted.testSteps());
            notes.add("Exec steps drafted from Maven exec-shaped plugins carry a placeholder input ("
                    + MavenExecStepDrafter.INPUT_PLACEHOLDER + ") and a conventional output path;"
                    + " declare the real declared-input closure and owned output for each before building.");
        }
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
                buildSettings,
                NativeSettings.defaults(),
                CompilerSettings.defaults(),
                PackageSettings.defaults());
        if (!dependencyMetadata.isEmpty()) {
            config = config.withDependencyMetadata(dependencyMetadata);
        }
        if (!constraints.isEmpty()) {
            config = config.withDependencyPolicy(new DependencyPolicySettings(List.of(), constraints));
        }
        return new DraftZoltToml(
                config,
                notes,
                List.copyOf(commentedProjectKeys),
                suggestPlatformApiHost);
    }

    private static String platformApiHostNote(String javaVersion) {
        return "This POM set source/target " + javaVersion + " below the build JDK, so Maven compiled"
                + " against the host JDK's API surface. Zolt defaults to the reproducible `--release "
                + javaVersion + "`. If the strict build fails because a dependency or annotation processor"
                + " uses a newer-than-" + javaVersion + " platform API, uncomment `platformApi = \"host\"`"
                + " under [compiler]; note that host mode forfeits cross-JDK reproducibility — prefer"
                + " raising [project].java or a multi-release JAR.";
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

    private static void addTestJavaVersionNote(MavenProjectInspection project, List<String> notes) {
        if (project.testJavaVersion().isBlank() || project.testJavaVersion().equals(project.javaVersion())) {
            return;
        }
        notes.add(
                "Maven test Java version `" + project.testJavaVersion()
                        + "` differs from main Java version `" + project.javaVersion()
                        + "`; Zolt has no separate test Java key yet, so review test compilation before use.");
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
            if (VersionPolicy.isSnapshot(version)) {
                notes.add(
                        "Project version `" + version + "` is a SNAPSHOT, a documented non-determinism"
                                + " signal (version-policy rule: snapshot-version); it resolves as-is but"
                                + " pin it to a fixed release before relying on the draft.");
            }
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
