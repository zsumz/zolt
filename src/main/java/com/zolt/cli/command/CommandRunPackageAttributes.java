package com.zolt.cli.command;

import com.zolt.build.RunPackageResult;
import com.zolt.workspace.WorkspaceRunPackageResult;
import java.util.LinkedHashMap;
import java.util.Map;

final class CommandRunPackageAttributes {
    private CommandRunPackageAttributes() {
    }

    static Map<String, String> runPackage(RunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MODE, result.packageResult().mode().configValue());
        attributes.put(TimingAttributeKeys.ENTRIES, Integer.toString(result.packageResult().entryCount()));
        attributes.put(TimingAttributeKeys.HAS_MAIN_CLASS, Boolean.toString(result.packageResult().hasMainClass()));
        attributes.put(TimingAttributeKeys.MAIN_CLASS, result.javaRunResult().mainClass());
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_SKIPPED, Boolean.toString(result.packageResult().buildResult().mainCompilationSkipped()));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATION_MODE, result.packageResult().buildResult().mainCompilationMode());
        attributes.put(
                TimingAttributeKeys.MAIN_INCREMENTAL_FALLBACK_REASON,
                result.packageResult().buildResult().mainIncrementalFallbackReason());
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.packageResult().buildResult().resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(result.javaRunResult().output().length()));
        return attributes;
    }

    static Map<String, String> workspaceRunPackage(WorkspaceRunPackageResult result) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()));
        attributes.put(TimingAttributeKeys.MAIN_SOURCE_FILES, Integer.toString(workspaceRunPackageSourceCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_SKIPPED, Integer.toString(workspaceRunPackageMainCompilationSkippedCount(result)));
        attributes.put(TimingAttributeKeys.MAIN_COMPILATIONS_EXECUTED, Integer.toString(workspaceRunPackageMainCompilationExecutedCount(result)));
        attributes.put(TimingAttributeKeys.ENTRIES, Integer.toString(workspaceRunPackageEntryCount(result)));
        attributes.put(TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
        attributes.put(TimingAttributeKeys.OUTPUT_BYTES, Integer.toString(workspaceRunPackageOutputBytes(result)));
        return attributes;
    }

    private static int workspaceRunPackageSourceCount(WorkspaceRunPackageResult result) {
        return result.builtMembers().stream()
                .mapToInt(member -> member.result().sourceCount())
                .sum();
    }

    private static int workspaceRunPackageMainCompilationSkippedCount(WorkspaceRunPackageResult result) {
        return (int) result.builtMembers().stream()
                .filter(member -> member.result().mainCompilationSkipped())
                .count();
    }

    private static int workspaceRunPackageMainCompilationExecutedCount(WorkspaceRunPackageResult result) {
        return result.builtMembers().size() - workspaceRunPackageMainCompilationSkippedCount(result);
    }

    private static int workspaceRunPackageEntryCount(WorkspaceRunPackageResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().packageResult().entryCount())
                .sum();
    }

    private static int workspaceRunPackageOutputBytes(WorkspaceRunPackageResult result) {
        return result.members().stream()
                .mapToInt(member -> member.result().javaRunResult().output().length())
                .sum();
    }
}
