package com.zolt.toml;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class ProjectConfigDependencyMutator {
    private ProjectConfigDependencyMutator() {
    }

    static ProjectConfig addDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String version) {
        validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, coordinate, version);
        ProjectConfigDependencyState state = ProjectConfigDependencyState.from(config);
        state = productionSection(section)
                ? state.removeProductionDependency(coordinate)
                : state.removeDependency(section, coordinate);
        return state.withVersion(section, coordinate, version).toConfig(config);
    }

    static ProjectConfig addVersionRefDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String versionRef,
            String version) {
        validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, coordinate, version);
        ProjectConfig updated = addDependency(config, section, coordinate, version);
        String sectionName = sectionName(section);
        DependencyMetadata existing = config.dependencyMetadata()
                .get(DependencyMetadata.key(sectionName, coordinate));
        DependencyMetadata metadata = new DependencyMetadata(
                sectionName,
                coordinate,
                version,
                versionRef,
                false,
                null,
                existing != null && existing.optional(),
                existing != null && existing.publishOnly(),
                existing == null ? List.of() : existing.exclusions());
        Map<String, DependencyMetadata> dependencyMetadata = new LinkedHashMap<>(updated.dependencyMetadata());
        dependencyMetadata.put(DependencyMetadata.key(sectionName, coordinate), metadata);
        return updated.withDependencyMetadata(dependencyMetadata);
    }

    static ProjectConfig addManagedDependency(ProjectConfig config, DependencySection section, String coordinate) {
        ProjectConfigDependencyState state = ProjectConfigDependencyState.from(config);
        state = productionSection(section)
                ? state.removeProductionDependency(coordinate)
                : state.removeDependency(section, coordinate);
        return state.withManaged(section, coordinate).toConfig(config);
    }

    static ProjectConfig removeDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return ProjectConfigDependencyState.from(config)
                .removeDependency(section, coordinate)
                .toConfig(config);
    }

    static ProjectConfig addPlatform(ProjectConfig config, String coordinate, String version) {
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = ProjectConfigDependencyState.from(config)
                .withPlatform(coordinate, version)
                .toConfig(config);
        return updated.withDependencyMetadata(removePlatformMetadata(updated.dependencyMetadata(), coordinate));
    }

    static ProjectConfig addVersionRefPlatform(
            ProjectConfig config,
            String coordinate,
            String versionRef,
            String version) {
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = ProjectConfigDependencyState.from(config)
                .withPlatform(coordinate, version)
                .toConfig(config);
        Map<String, DependencyMetadata> metadata = new LinkedHashMap<>(updated.dependencyMetadata());
        metadata.put(
                DependencyMetadata.key("platforms", coordinate),
                new DependencyMetadata(
                        "platforms",
                        coordinate,
                        version,
                        versionRef,
                        false,
                        null,
                        false,
                        false,
                        List.of()));
        return updated.withDependencyMetadata(metadata);
    }

    static ProjectConfig removePlatform(ProjectConfig config, String coordinate) {
        ProjectConfig updated = ProjectConfigDependencyState.from(config)
                .withoutPlatform(coordinate)
                .toConfig(config);
        return updated.withDependencyMetadata(removePlatformMetadata(updated.dependencyMetadata(), coordinate));
    }

    private static boolean productionSection(DependencySection section) {
        return switch (section) {
            case API, MAIN, RUNTIME, PROVIDED, DEV -> true;
            case TEST, PROCESSOR, TEST_PROCESSOR -> false;
        };
    }

    private static Map<String, DependencyMetadata> removePlatformMetadata(
            Map<String, DependencyMetadata> dependencyMetadata,
            String coordinate) {
        if (!dependencyMetadata.containsKey(DependencyMetadata.key("platforms", coordinate))) {
            return dependencyMetadata;
        }
        Map<String, DependencyMetadata> metadata = new LinkedHashMap<>(dependencyMetadata);
        metadata.remove(DependencyMetadata.key("platforms", coordinate));
        return metadata;
    }

    private static void validateVersion(
            VersionPolicy.Context context,
            String coordinate,
            String version) {
        VersionPolicy.violation(context, version).ifPresent(violation -> {
            throw new IllegalArgumentException(
                    "Invalid "
                            + context.description()
                            + " `"
                            + version
                            + "` for "
                            + coordinate
                            + ". "
                            + violation.guidance());
        });
    }

    private static String sectionName(DependencySection section) {
        return switch (section) {
            case MAIN -> "dependencies";
            case API -> "api.dependencies";
            case RUNTIME -> "runtime.dependencies";
            case PROVIDED -> "provided.dependencies";
            case DEV -> "dev.dependencies";
            case TEST -> "test.dependencies";
            case PROCESSOR -> "annotationProcessors";
            case TEST_PROCESSOR -> "test.annotationProcessors";
        };
    }

}
