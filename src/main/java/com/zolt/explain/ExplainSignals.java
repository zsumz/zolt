package com.zolt.explain;

import java.util.Comparator;
import java.util.List;

public final class ExplainSignals {
    public static final ExplainSignalDefinition MAVEN_MODULE_MISSING_POM = new ExplainSignalDefinition(
            "maven.module.missing-pom",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.BUILDABILITY,
            "Fix the module path or exclude it from migration scope before generating Zolt metadata.");
    public static final ExplainSignalDefinition MAVEN_PACKAGING_UNSUPPORTED = new ExplainSignalDefinition(
            "maven.packaging.unsupported",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Map this packaging mode to Zolt package settings before migrating.");
    public static final ExplainSignalDefinition MAVEN_DEPENDENCY_DYNAMIC_VERSION = new ExplainSignalDefinition(
            "maven.dependency.dynamic-version",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.NON_DETERMINISM,
            "Replace Maven ranges or SNAPSHOTs with fixed versions before migrating.");
    public static final ExplainSignalDefinition MAVEN_PLUGIN_LIFECYCLE_BINDING = new ExplainSignalDefinition(
            "maven.plugin.lifecycle-binding",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Replace plugin-generated behavior with explicit Zolt configuration or keep it in manual-review scope.");
    public static final ExplainSignalDefinition MAVEN_PLUGIN_STATIC_SIGNAL = new ExplainSignalDefinition(
            "maven.plugin.static-signal",
            ExplainSignal.Severity.WARN,
            ExplainSignal.Category.BUILDABILITY,
            "Review whether this plugin changes compilation, resources, tests, packaging, or generated outputs.");
    public static final ExplainSignalDefinition MAVEN_PROFILE_DETECTED = new ExplainSignalDefinition(
            "maven.profile.detected",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.NON_DETERMINISM,
            "Move profile-specific behavior into explicit Zolt configuration or keep it in manual-review scope.");

    public static final ExplainSignalDefinition GRADLE_BUILD_SRC_DETECTED = new ExplainSignalDefinition(
            "gradle.build-src.detected",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Review buildSrc logic and replace generated behavior with explicit Zolt configuration before migrating.");
    public static final ExplainSignalDefinition GRADLE_PROJECT_MISSING_BUILD_FILE = new ExplainSignalDefinition(
            "gradle.project.missing-build-file",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.BUILDABILITY,
            "Fix the include path or exclude it from migration scope before generating Zolt metadata.");
    public static final ExplainSignalDefinition GRADLE_PLUGIN_CONVENTION = new ExplainSignalDefinition(
            "gradle.plugin.convention",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Review the plugin implementation and model its effects explicitly in Zolt before migrating.");
    public static final ExplainSignalDefinition GRADLE_INCLUDED_BUILD_DETECTED = new ExplainSignalDefinition(
            "gradle.included-build.detected",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Review included build logic and migrate it as an explicit Zolt workspace or external dependency.");
    public static final ExplainSignalDefinition GRADLE_IMPERATIVE_DEPENDENCY_LOGIC = new ExplainSignalDefinition(
            "gradle.imperative-dependency-logic",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Replace imperative Gradle logic with explicit Zolt dependencies, platforms, processors, and source roots.");
    public static final ExplainSignalDefinition GRADLE_DEPENDENCY_DYNAMIC_VERSION = new ExplainSignalDefinition(
            "gradle.dependency.dynamic-version",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.NON_DETERMINISM,
            "Replace Gradle dynamic versions with fixed versions or explicit platforms before migrating.");
    public static final ExplainSignalDefinition GRADLE_CROSS_PROJECT_BUILD_LOGIC = new ExplainSignalDefinition(
            "gradle.cross-project-build-logic",
            ExplainSignal.Severity.BLOCK,
            ExplainSignal.Category.MIGRATION_BLOCKER,
            "Move shared behavior into explicit per-member Zolt configuration or a documented workspace convention.");
    public static final ExplainSignalDefinition GRADLE_CUSTOM_TASK_DETECTED = new ExplainSignalDefinition(
            "gradle.custom-task.detected",
            ExplainSignal.Severity.WARN,
            ExplainSignal.Category.BUILDABILITY,
            "Review whether custom tasks generate sources, resources, tests, package outputs, or runtime assets.");
    public static final ExplainSignalDefinition GRADLE_VERSION_CATALOG_MALFORMED = new ExplainSignalDefinition(
            "gradle.version-catalog.malformed",
            ExplainSignal.Severity.WARN,
            ExplainSignal.Category.BUILDABILITY,
            "Fix gradle/libs.versions.toml before relying on version catalog migration hints.");

    private static final List<ExplainSignalDefinition> DEFINITIONS = List.of(
            MAVEN_MODULE_MISSING_POM,
            MAVEN_PACKAGING_UNSUPPORTED,
            MAVEN_DEPENDENCY_DYNAMIC_VERSION,
            MAVEN_PLUGIN_LIFECYCLE_BINDING,
            MAVEN_PLUGIN_STATIC_SIGNAL,
            MAVEN_PROFILE_DETECTED,
            GRADLE_BUILD_SRC_DETECTED,
            GRADLE_PROJECT_MISSING_BUILD_FILE,
            GRADLE_PLUGIN_CONVENTION,
            GRADLE_INCLUDED_BUILD_DETECTED,
            GRADLE_IMPERATIVE_DEPENDENCY_LOGIC,
            GRADLE_DEPENDENCY_DYNAMIC_VERSION,
            GRADLE_CROSS_PROJECT_BUILD_LOGIC,
            GRADLE_CUSTOM_TASK_DETECTED,
            GRADLE_VERSION_CATALOG_MALFORMED);
    private static final Comparator<ExplainSignal> COMPARATOR = Comparator
            .comparingInt((ExplainSignal signal) -> severityRank(signal.severity()))
            .thenComparingInt(signal -> categoryRank(signal.category()))
            .thenComparing(ExplainSignal::project)
            .thenComparing(ExplainSignal::id)
            .thenComparing(ExplainSignal::message);

    private ExplainSignals() {
    }

    public static List<ExplainSignalDefinition> definitions() {
        return DEFINITIONS;
    }

    public static List<ExplainSignal> sorted(List<ExplainSignal> signals) {
        return signals.stream()
                .sorted(COMPARATOR)
                .toList();
    }

    private static int severityRank(ExplainSignal.Severity severity) {
        return switch (severity) {
            case BLOCK -> 0;
            case UNKNOWN -> 1;
            case WARN -> 2;
            case OK -> 3;
        };
    }

    private static int categoryRank(ExplainSignal.Category category) {
        return switch (category) {
            case BUILDABILITY -> 0;
            case CACHEABILITY -> 1;
            case NON_DETERMINISM -> 2;
            case MIGRATION_BLOCKER -> 3;
        };
    }
}
