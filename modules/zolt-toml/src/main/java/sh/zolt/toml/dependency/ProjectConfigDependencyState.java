package sh.zolt.toml.dependency;

import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

record ProjectConfigDependencyState(
        Map<String, String> platforms,
        ProjectConfigDependencyState.DependencyBucket api,
        ProjectConfigDependencyState.DependencyBucket main,
        ProjectConfigDependencyState.DependencyBucket runtime,
        ProjectConfigDependencyState.DependencyBucket provided,
        ProjectConfigDependencyState.DependencyBucket dev,
        ProjectConfigDependencyState.DependencyBucket test,
        ProjectConfigDependencyState.DependencyBucket processors,
        ProjectConfigDependencyState.DependencyBucket testProcessors) {
    static ProjectConfigDependencyState from(ProjectConfig config) {
        return new ProjectConfigDependencyState(
                config.platforms(),
                new DependencyBucket(
                        config.apiDependencies(), config.managedApiDependencies(), config.workspaceApiDependencies()),
                new DependencyBucket(
                        config.dependencies(), config.managedDependencies(), config.workspaceDependencies()),
                new DependencyBucket(config.runtimeDependencies(), config.managedRuntimeDependencies(), Map.of()),
                new DependencyBucket(config.providedDependencies(), config.managedProvidedDependencies(), Map.of()),
                new DependencyBucket(config.devDependencies(), config.managedDevDependencies(), Map.of()),
                new DependencyBucket(
                        config.testDependencies(),
                        config.managedTestDependencies(),
                        config.workspaceTestDependencies()),
                new DependencyBucket(
                        config.annotationProcessors(),
                        config.managedAnnotationProcessors(),
                        config.workspaceAnnotationProcessors()),
                new DependencyBucket(
                        config.testAnnotationProcessors(),
                        config.managedTestAnnotationProcessors(),
                        config.workspaceTestAnnotationProcessors()));
    }

    ProjectConfigDependencyState withPlatform(String coordinate, String version) {
        return withPlatforms(put(platforms, coordinate, version));
    }

    ProjectConfigDependencyState withoutPlatform(String coordinate) {
        return withPlatforms(remove(platforms, coordinate));
    }

    ProjectConfigDependencyState withVersion(DependencySection section, String coordinate, String version) {
        return replace(section, section(section).withVersion(coordinate, version));
    }

    ProjectConfigDependencyState withManaged(DependencySection section, String coordinate) {
        return replace(section, section(section).withManaged(coordinate));
    }

    ProjectConfigDependencyState removeDependency(DependencySection section, String coordinate) {
        return replace(section, section(section).without(coordinate));
    }

    ProjectConfigDependencyState removeProductionDependency(String coordinate) {
        return new ProjectConfigDependencyState(
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

    ProjectConfig toConfig(ProjectConfig config) {
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
                processors.workspace(),
                testProcessors.versioned(),
                testProcessors.managed(),
                testProcessors.workspace(),
                config.dependencyPolicy(),
                config.build(),
                config.nativeSettings(),
                config.compilerSettings(),
                config.packageSettings(),
                config.frameworkSettings(),
                retainedDependencyMetadata(config.dependencyMetadata()));
    }

    private ProjectConfigDependencyState withPlatforms(Map<String, String> platforms) {
        return new ProjectConfigDependencyState(
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

    private ProjectConfigDependencyState replace(DependencySection section, DependencyBucket bucket) {
        return switch (section) {
            case API -> new ProjectConfigDependencyState(
                    platforms, bucket, main, runtime, provided, dev, test, processors, testProcessors);
            case MAIN -> new ProjectConfigDependencyState(
                    platforms, api, bucket, runtime, provided, dev, test, processors, testProcessors);
            case RUNTIME -> new ProjectConfigDependencyState(
                    platforms, api, main, bucket, provided, dev, test, processors, testProcessors);
            case PROVIDED -> new ProjectConfigDependencyState(
                    platforms, api, main, runtime, bucket, dev, test, processors, testProcessors);
            case DEV -> new ProjectConfigDependencyState(
                    platforms, api, main, runtime, provided, bucket, test, processors, testProcessors);
            case TEST -> new ProjectConfigDependencyState(
                    platforms, api, main, runtime, provided, dev, bucket, processors, testProcessors);
            case PROCESSOR -> new ProjectConfigDependencyState(
                    platforms, api, main, runtime, provided, dev, test, bucket, testProcessors);
            case TEST_PROCESSOR -> new ProjectConfigDependencyState(
                    platforms, api, main, runtime, provided, dev, test, processors, bucket);
        };
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
}
