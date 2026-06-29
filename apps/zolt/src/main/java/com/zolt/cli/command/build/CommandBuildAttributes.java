package com.zolt.cli.command.build;

import com.zolt.build.BuildResult;
import com.zolt.build.CompileDiagnostics;
import com.zolt.cli.command.CommandAttributeKeys;
import com.zolt.framework.FrameworkBuildAugmentationResult;
import com.zolt.workspace.WorkspaceBuildPlan;
import com.zolt.workspace.WorkspaceBuildResult;
import com.zolt.workspace.WorkspaceSelection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class CommandBuildAttributes {
    private CommandBuildAttributes() {
    }

    public static Map<String, String> build(BuildResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CommandAttributeKeys.SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(CommandAttributeKeys.RESOURCE_FILES, Integer.toString(result.resourceCount()));
        attributes.put(CommandAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(CommandAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.mainCompilationSkipped()));
        attributes.put(CommandAttributeKeys.MAIN_COMPILATION_MODE, result.mainCompilationMode());
        attributes.put(CommandAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.mainIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.mainCompileDiagnostics());
        addMainFingerprintAttributes(attributes, result);
        return attributes;
    }

    public static Map<String, String> workspaceBuild(WorkspaceBuildResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CommandAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(CommandAttributeKeys.SOURCE_FILES, Integer.toString(result.sourceCount()));
        attributes.put(CommandAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(result.mainCompilationSkippedCount()));
        attributes.put(CommandAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(result.mainCompilationExecutedCount()));
        addMainCompileDiagnostics(attributes, result.mainCompileDiagnostics());
        attributes.put(CommandAttributeKeys.WORKSPACE_ABI_INVALIDATIONS, Integer.toString(result.workspaceAbiInvalidationCount()));
        attributes.put(CommandAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
        return attributes;
    }

    public static Map<String, String> workspaceBuild(WorkspaceBuildResult result, WorkspaceSelection selection) {
        Map<String, String> attributes = workspaceBuild(result);
        attributes.put(CommandAttributeKeys.INCLUDED_MEMBERS, Integer.toString(selection.includedMembers().size()));
        attributes.put(CommandAttributeKeys.SELECTED_MEMBERS, Integer.toString(selection.selectedMembers().size()));
        attributes.put(CommandAttributeKeys.DEPENDENCY_MEMBERS, Integer.toString(selection.includedMembers().size() - selection.selectedMembers().size()));
        return attributes;
    }

    public static Map<String, String> workspaceBuildPlan(WorkspaceBuildPlan plan) {
        return Map.of(
                CommandAttributeKeys.INCLUDED_MEMBERS, Integer.toString(plan.selection().includedMembers().size()),
                CommandAttributeKeys.SELECTED_MEMBERS, Integer.toString(plan.selection().selectedMembers().size()),
                CommandAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(plan.resolvedLockfile()));
    }

    public static Map<String, String> frameworkAugmentation(Optional<FrameworkBuildAugmentationResult> result) {
        if (result.isEmpty()) {
            return Map.of(CommandAttributeKeys.ENABLED, "false");
        }
        FrameworkBuildAugmentationResult augmentation = result.orElseThrow();
        return Map.of(
                CommandAttributeKeys.ENABLED, "true",
                CommandAttributeKeys.RUNNER_JAR, augmentation.runnerJar().toString());
    }

    private static void addMainCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        CompileDiagnostics values = diagnostics == null ? CompileDiagnostics.empty() : diagnostics;
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.SOURCES_ADDED_SUFFIX, Integer.toString(values.sourcesAdded()));
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.SOURCES_CHANGED_SUFFIX, Integer.toString(values.sourcesChanged()));
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.SOURCES_DELETED_SUFFIX, Integer.toString(values.sourcesDeleted()));
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.SOURCES_RECOMPILED_SUFFIX, Integer.toString(values.sourcesRecompiled()));
        attributes.put(
                CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.DEPENDENT_SOURCES_RECOMPILED_SUFFIX,
                Integer.toString(values.dependentSourcesRecompiled()));
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.CLASSES_DELETED_SUFFIX, Integer.toString(values.classesDeleted()));
        attributes.put(CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.ABI_CHANGED_CLASSES_SUFFIX, Integer.toString(values.abiChangedClasses()));
        attributes.put(
                CommandAttributeKeys.MAIN_PREFIX + CommandAttributeKeys.PACKAGE_PRIVATE_ABI_CHANGED_CLASSES_SUFFIX,
                Integer.toString(values.packagePrivateAbiChangedClasses()));
    }

    private static void addMainFingerprintAttributes(Map<String, String> attributes, BuildResult result) {
        addMainFingerprintAttributes(
                attributes,
                result.mainFingerprintCheckNanos(),
                result.mainFingerprintWriteNanos());
    }

    private static void addMainFingerprintAttributes(
            Map<String, String> attributes,
            long checkNanos,
            long writeNanos) {
        attributes.put(CommandAttributeKeys.MAIN_FINGERPRINT_CHECK_MILLIS, Long.toString(checkNanos / 1_000_000L));
        attributes.put(CommandAttributeKeys.MAIN_FINGERPRINT_CHECK_NANOS, Long.toString(checkNanos));
        attributes.put(CommandAttributeKeys.MAIN_FINGERPRINT_WRITE_MILLIS, Long.toString(writeNanos / 1_000_000L));
        attributes.put(CommandAttributeKeys.MAIN_FINGERPRINT_WRITE_NANOS, Long.toString(writeNanos));
    }
}
