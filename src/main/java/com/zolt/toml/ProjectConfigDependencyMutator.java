package com.zolt.toml;

import com.zolt.project.DependencyMetadata;
import com.zolt.project.DependencySection;
import com.zolt.project.ProjectConfig;
import com.zolt.project.VersionPolicy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

final class ProjectConfigDependencyMutator {
    private ProjectConfigDependencyMutator() {
    }

    static ProjectConfig addDependency(
            ProjectConfig config,
            DependencySection section,
            String coordinate,
            String version) {
        validateVersion(VersionPolicy.Context.EXTERNAL_DEPENDENCY, coordinate, version);
        DependencyState state = DependencyState.from(config);
        state = productionSection(section)
                ? state.removeProductionDependency(coordinate)
                : state.removeDependency(section, coordinate);
        return state.replace(section, state.section(section).withVersion(coordinate, version))
                .toConfig(config);
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
        DependencyState state = DependencyState.from(config);
        state = productionSection(section)
                ? state.removeProductionDependency(coordinate)
                : state.removeDependency(section, coordinate);
        return state.replace(section, state.section(section).withManaged(coordinate))
                .toConfig(config);
    }

    static ProjectConfig removeDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return DependencyState.from(config)
                .removeDependency(section, coordinate)
                .toConfig(config);
    }

    static ProjectConfig addPlatform(ProjectConfig config, String coordinate, String version) {
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = DependencyState.from(config)
                .withPlatforms(put(config.platforms(), coordinate, version))
                .toConfig(config);
        return updated.withDependencyMetadata(removePlatformMetadata(updated.dependencyMetadata(), coordinate));
    }

    static ProjectConfig addVersionRefPlatform(
            ProjectConfig config,
            String coordinate,
            String versionRef,
            String version) {
        validateVersion(VersionPolicy.Context.PLATFORM, coordinate, version);
        ProjectConfig updated = DependencyState.from(config)
                .withPlatforms(put(config.platforms(), coordinate, version))
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
        ProjectConfig updated = DependencyState.from(config)
                .withPlatforms(remove(config.platforms(), coordinate))
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

    private static Map<String, String> put(Map<String, String> source, String coordinate, String version) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.put(coordinate, version);
        return updated;
    }

    private static Map<String, String> remove(Map<String, String> source, String coordinate) {
        Map<String, String> updated = new LinkedHashMap<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private static Set<String> add(Set<String> source, String coordinate) {
        TreeSet<String> updated = new TreeSet<>(source);
        updated.add(coordinate);
        return updated;
    }

    private static Set<String> remove(Set<String> source, String coordinate) {
        TreeSet<String> updated = new TreeSet<>(source);
        updated.remove(coordinate);
        return updated;
    }

    private record DependencyBucket(
            Map<String, String> versioned,
            Set<String> managed,
            Map<String, String> workspace) {
        private DependencyBucket withVersion(String coordinate, String version) {
            return new DependencyBucket(
                    put(versioned, coordinate, version),
                    remove(managed, coordinate),
                    remove(workspace, coordinate));
        }

        private DependencyBucket withManaged(String coordinate) {
            return new DependencyBucket(
                    remove(versioned, coordinate),
                    add(managed, coordinate),
                    remove(workspace, coordinate));
        }

        private DependencyBucket without(String coordinate) {
            return new DependencyBucket(
                    remove(versioned, coordinate),
                    remove(managed, coordinate),
                    remove(workspace, coordinate));
        }

        private boolean contains(String coordinate) {
            return versioned.containsKey(coordinate)
                    || managed.contains(coordinate)
                    || workspace.containsKey(coordinate);
        }
    }

    private record DependencyState(
            Map<String, String> platforms,
            DependencyBucket api,
            DependencyBucket main,
            DependencyBucket runtime,
            DependencyBucket provided,
            DependencyBucket dev,
            DependencyBucket test,
            DependencyBucket processors,
            DependencyBucket testProcessors) {
        private static DependencyState from(ProjectConfig config) {
            return new DependencyState(
                    config.platforms(),
                    new DependencyBucket(
                            config.apiDependencies(),
                            config.managedApiDependencies(),
                            config.workspaceApiDependencies()),
                    new DependencyBucket(
                            config.dependencies(),
                            config.managedDependencies(),
                            config.workspaceDependencies()),
                    new DependencyBucket(
                            config.runtimeDependencies(),
                            config.managedRuntimeDependencies(),
                            Map.of()),
                    new DependencyBucket(
                            config.providedDependencies(),
                            config.managedProvidedDependencies(),
                            Map.of()),
                    new DependencyBucket(
                            config.devDependencies(),
                            config.managedDevDependencies(),
                            Map.of()),
                    new DependencyBucket(
                            config.testDependencies(),
                            config.managedTestDependencies(),
                            config.workspaceTestDependencies()),
                    new DependencyBucket(
                            config.annotationProcessors(),
                            config.managedAnnotationProcessors(),
                            Map.of()),
                    new DependencyBucket(
                            config.testAnnotationProcessors(),
                            config.managedTestAnnotationProcessors(),
                            Map.of()));
        }

        private DependencyState withPlatforms(Map<String, String> platforms) {
            return new DependencyState(
                    platforms,
                    api,
                    main,
                    runtime,
                    provided,
                    dev,
                    test,
                    processors,
                    testProcessors);
        }

        private DependencyBucket section(DependencySection section) {
            return switch (section) {
                case API -> api;
                case MAIN -> main;
                case RUNTIME -> runtime;
                case PROVIDED -> provided;
                case DEV -> dev;
                case TEST -> test;
                case PROCESSOR -> processors;
                case TEST_PROCESSOR -> testProcessors;
            };
        }

        private DependencyState replace(DependencySection section, DependencyBucket bucket) {
            return switch (section) {
                case API -> new DependencyState(
                        platforms, bucket, main, runtime, provided, dev, test, processors, testProcessors);
                case MAIN -> new DependencyState(
                        platforms, api, bucket, runtime, provided, dev, test, processors, testProcessors);
                case RUNTIME -> new DependencyState(
                        platforms, api, main, bucket, provided, dev, test, processors, testProcessors);
                case PROVIDED -> new DependencyState(
                        platforms, api, main, runtime, bucket, dev, test, processors, testProcessors);
                case DEV -> new DependencyState(
                        platforms, api, main, runtime, provided, bucket, test, processors, testProcessors);
                case TEST -> new DependencyState(
                        platforms, api, main, runtime, provided, dev, bucket, processors, testProcessors);
                case PROCESSOR -> new DependencyState(
                        platforms, api, main, runtime, provided, dev, test, bucket, testProcessors);
                case TEST_PROCESSOR -> new DependencyState(
                        platforms, api, main, runtime, provided, dev, test, processors, bucket);
            };
        }

        private DependencyState removeDependency(DependencySection section, String coordinate) {
            return replace(section, section(section).without(coordinate));
        }

        private DependencyState removeProductionDependency(String coordinate) {
            return new DependencyState(
                    platforms,
                    api.without(coordinate),
                    main.without(coordinate),
                    runtime.without(coordinate),
                    provided.without(coordinate),
                    dev.without(coordinate),
                    test,
                    processors,
                    testProcessors);
        }

        private ProjectConfig toConfig(ProjectConfig config) {
            return new ProjectConfig(
                    config.project(),
                    config.repositories(),
                    config.repositorySettings(),
                    config.repositoryCredentials(),
                    config.versionAliases(),
                    platforms,
                    api.versioned(),
                    api.managed(),
                    api.workspace(),
                    main.versioned(),
                    main.managed(),
                    main.workspace(),
                    runtime.versioned(),
                    runtime.managed(),
                    provided.versioned(),
                    provided.managed(),
                    dev.versioned(),
                    dev.managed(),
                    test.versioned(),
                    test.managed(),
                    test.workspace(),
                    processors.versioned(),
                    processors.managed(),
                    testProcessors.versioned(),
                    testProcessors.managed(),
                    config.dependencyPolicy(),
                    config.build(),
                    config.nativeSettings(),
                    config.compilerSettings(),
                    config.packageSettings(),
                    config.frameworkSettings(),
                    retainedDependencyMetadata(config.dependencyMetadata()));
        }

        private Map<String, DependencyMetadata> retainedDependencyMetadata(Map<String, DependencyMetadata> metadata) {
            Map<String, DependencyMetadata> retained = new LinkedHashMap<>();
            for (DependencyMetadata value : metadata.values()) {
                if (value.publishOnly() || containsDependency(value.section(), value.coordinate())) {
                    retained.put(DependencyMetadata.key(value.section(), value.coordinate()), value);
                }
            }
            return retained;
        }

        private boolean containsDependency(String section, String coordinate) {
            return switch (section) {
                case "platforms" -> platforms.containsKey(coordinate);
                case "api.dependencies" -> api.contains(coordinate);
                case "dependencies" -> main.contains(coordinate);
                case "runtime.dependencies" -> runtime.contains(coordinate);
                case "provided.dependencies" -> provided.contains(coordinate);
                case "dev.dependencies" -> dev.contains(coordinate);
                case "test.dependencies" -> test.contains(coordinate);
                case "annotationProcessors" -> processors.contains(coordinate);
                case "test.annotationProcessors" -> testProcessors.contains(coordinate);
                default -> false;
            };
        }
    }
}
