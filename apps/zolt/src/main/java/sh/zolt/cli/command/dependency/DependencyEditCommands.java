package sh.zolt.cli.command.dependency;

import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.DependencySection;
import sh.zolt.project.ProjectConfig;
import sh.zolt.project.VersionPolicy;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

final class DependencyEditCommands {
    private DependencyEditCommands() {
    }

    record AddRequest(
            DependencySection section,
            String coordinate,
            String version,
            boolean managed,
            String versionRef) {
    }

    record RemoveRequest(DependencySection section, String coordinate) {
    }

    static Map<String, String> dependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.dependencies();
            case API -> config.apiDependencies();
            case RUNTIME -> config.runtimeDependencies();
            case PROVIDED -> config.providedDependencies();
            case DEV -> config.devDependencies();
            case TEST -> config.testDependencies();
            case PROCESSOR -> config.annotationProcessors();
            case TEST_PROCESSOR -> config.testAnnotationProcessors();
        };
    }

    static Set<String> managedDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.managedDependencies();
            case API -> config.managedApiDependencies();
            case RUNTIME -> config.managedRuntimeDependencies();
            case PROVIDED -> config.managedProvidedDependencies();
            case DEV -> config.managedDevDependencies();
            case TEST -> config.managedTestDependencies();
            case PROCESSOR -> config.managedAnnotationProcessors();
            case TEST_PROCESSOR -> config.managedTestAnnotationProcessors();
        };
    }

    static Map<String, String> workspaceDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.workspaceDependencies();
            case API -> config.workspaceApiDependencies();
            case TEST -> config.workspaceTestDependencies();
            case RUNTIME, PROVIDED, DEV, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    static Map<String, String> conflictingDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> combinedDependencies(
                    config.apiDependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case API -> combinedDependencies(
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case RUNTIME -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.providedDependencies(),
                    config.devDependencies());
            case PROVIDED -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.devDependencies());
            case DEV -> combinedDependencies(
                    config.apiDependencies(),
                    config.dependencies(),
                    config.runtimeDependencies(),
                    config.providedDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    static Set<String> conflictingManagedDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case API -> combinedManagedDependencies(
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case RUNTIME -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedProvidedDependencies(),
                    config.managedDevDependencies());
            case PROVIDED -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedDevDependencies());
            case DEV -> combinedManagedDependencies(
                    config.managedApiDependencies(),
                    config.managedDependencies(),
                    config.managedRuntimeDependencies(),
                    config.managedProvidedDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> Set.of();
        };
    }

    static Map<String, String> conflictingWorkspaceDependencies(ProjectConfig config, DependencySection section) {
        return switch (section) {
            case MAIN -> config.workspaceApiDependencies();
            case API -> config.workspaceDependencies();
            case RUNTIME -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case PROVIDED -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case DEV -> combinedDependencies(config.workspaceApiDependencies(), config.workspaceDependencies());
            case TEST, PROCESSOR, TEST_PROCESSOR -> Map.of();
        };
    }

    @SafeVarargs
    private static Map<String, String> combinedDependencies(Map<String, String>... candidates) {
        Map<String, String> combined = new LinkedHashMap<>();
        for (Map<String, String> candidate : candidates) {
            combined.putAll(candidate);
        }
        return combined;
    }

    @SafeVarargs
    private static Set<String> combinedManagedDependencies(Set<String>... candidates) {
        Set<String> combined = new LinkedHashSet<>();
        for (Set<String> candidate : candidates) {
            combined.addAll(candidate);
        }
        return combined;
    }

    static String existingDescription(
            String version,
            boolean managed,
            String workspace) {
        if (version != null) {
            return version;
        }
        if (managed) {
            return "managed version";
        }
        return "workspace member " + workspace;
    }

    static String versionRef(ProjectConfig config, DependencySection section, String coordinate) {
        DependencyMetadata metadata = config.dependencyMetadata().get(DependencyMetadata.key(sectionName(section), coordinate));
        return metadata == null ? null : metadata.versionRef();
    }

    static boolean hasDependency(ProjectConfig config, DependencySection section, String coordinate) {
        return dependencies(config, section).containsKey(coordinate)
                || managedDependencies(config, section).contains(coordinate)
                || workspaceDependencies(config, section).containsKey(coordinate);
    }

    static String sectionName(DependencySection section) {
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

    static DependencySection parseSection(List<String> values, String command) {
        if (values.size() == 1) {
            return DependencySection.MAIN;
        }
        return switch (values.get(0)) {
            case "api" -> DependencySection.API;
            case "runtime" -> DependencySection.RUNTIME;
            case "provided" -> DependencySection.PROVIDED;
            case "dev" -> DependencySection.DEV;
            case "test" -> DependencySection.TEST;
            case "processor" -> DependencySection.PROCESSOR;
            case "test-processor" -> DependencySection.TEST_PROCESSOR;
            default -> throw new DependencySectionException("Unexpected dependency section `" + values.get(0)
                    + "`. Use `" + command + " api group:artifact`, `"
                    + command + " runtime group:artifact`, `"
                    + command + " provided group:artifact`, `"
                    + command + " dev group:artifact`, `"
                    + command + " test group:artifact`, `"
                    + command + " processor group:artifact`, or `"
                    + command + " test-processor group:artifact`.");
        };
    }

    static <T extends RuntimeException> void validateCommandVersion(
            VersionPolicy.Context context,
            String subject,
            String version,
            Function<String, T> exceptionFactory) {
        validateCommandVersion(context, subject, version, false, exceptionFactory);
    }

    static <T extends RuntimeException> void validateCommandVersion(
            VersionPolicy.Context context,
            String subject,
            String version,
            boolean snapshotPermitted,
            Function<String, T> exceptionFactory) {
        VersionPolicy.violation(context, version, snapshotPermitted).ifPresent(violation -> {
            throw exceptionFactory.apply(
                    "Invalid "
                            + context.description()
                            + " `"
                            + version
                            + "` for "
                            + subject
                            + ". "
                            + violation.guidance());
        });
    }

    static final class AddCommandException extends RuntimeException {
        AddCommandException(String message) {
            super(message);
        }
    }

    static final class RemoveCommandException extends RuntimeException {
        RemoveCommandException(String message) {
            super(message);
        }
    }

    static final class DependencySectionException extends RuntimeException {
        DependencySectionException(String message) {
            super(message);
        }
    }

    static final class PlatformCommandException extends RuntimeException {
        PlatformCommandException(String message) {
            super(message);
        }
    }
}
