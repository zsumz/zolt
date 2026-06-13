package com.zolt.cli.command;

import com.zolt.build.CompileDiagnostics;
import com.zolt.build.RunResult;
import com.zolt.workspace.WorkspaceRunResult;
import java.util.LinkedHashMap;
import java.util.Map;

final class CommandRunAttributes {
    private CommandRunAttributes() {
    }

    static Map<String, String> run(RunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MAIN_CLASS, result.javaRunResult().mainClass());
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(result.buildResult().sourceCount()));
        attributes.put(TimingAttributeKeys.RESOURCE_FILES, Integer.toString(result.buildResult().resourceCount()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.buildResult().mainCompilationMode());
        attributes.put(TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON, result.buildResult().mainIncrementalFallbackReason());
        addMainCompileDiagnostics(attributes, result.buildResult().mainCompileDiagnostics());
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.buildResult().resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    static Map<String, String> workspaceRun(WorkspaceRunResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(workspaceRunSourceCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(workspaceRunMainCompilationSkippedCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(workspaceRunMainCompilationExecutedCount(result)));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(workspaceRunOutputBytes(result)));
        return attributes;
    }

    private static void addMainCompileDiagnostics(Map<String, String> attributes, CompileDiagnostics diagnostics) {
        CompileDiagnostics values = diagnostics == null ? CompileDiagnostics.empty() : diagnostics;
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.SOURCES_ADDED_SUFFIX, Integer.toString(values.sourcesAdded()));
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.SOURCES_CHANGED_SUFFIX, Integer.toString(values.sourcesChanged()));
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.SOURCES_DELETED_SUFFIX, Integer.toString(values.sourcesDeleted()));
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.SOURCES_RECOMPILED_SUFFIX, Integer.toString(values.sourcesRecompiled()));
        attributes.put(
                TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.DEPENDENT_SOURCES_RECOMPILED_SUFFIX,
                Integer.toString(values.dependentSourcesRecompiled()));
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.CLASSES_DELETED_SUFFIX, Integer.toString(values.classesDeleted()));
        attributes.put(TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.ABI_CHANGED_CLASSES_SUFFIX, Integer.toString(values.abiChangedClasses()));
        attributes.put(
                TimingAttributeKeys.MAIN_PREFIX + TimingAttributeKeys.PACKAGE_PRIVATE_ABI_CHANGED_CLASSES_SUFFIX,
                Integer.toString(values.packagePrivateAbiChangedClasses()));
    }

    private static int workspaceRunSourceCount(WorkspaceRunResult result) {
        return result.builtMembers().stream()
                .mapToInt(member -> member.result().sourceCount())
                .sum();
    }

    private static int workspaceRunMainCompilationSkippedCount(WorkspaceRunResult result) {
        return (int) result.builtMembers().stream()
                .filter(member -> member.result().mainCompilationSkipped())
                .count();
    }

    private static int workspaceRunMainCompilationExecutedCount(WorkspaceRunResult result) {
        return result.builtMembers().size() - workspaceRunMainCompilationSkippedCount(result);
    }

    private static int workspaceRunOutputBytes(WorkspaceRunResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().javaRunResult().output().length())
                .sum();
    }
}
