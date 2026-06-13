package com.zolt.cli.command;

import com.zolt.build.PackagePlan;
import com.zolt.build.PackageResult;
import com.zolt.workspace.WorkspacePackageResult;
import java.util.LinkedHashMap;
import java.util.Map;

final class CommandPackageAttributes {
    private CommandPackageAttributes() {
    }

    static Map<String, String> packageResult(PackageResult result) {
        return Map.of(
                TimingAttributeKeys.MODE, result.mode().configValue(),
                TimingAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                TimingAttributeKeys.HAS_MAIN_CLASS, Boolean.toString(result.hasMainClass()),
                TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.buildResult().resolvedLockfile()));
    }

    static Map<String, String> workspacePackage(WorkspacePackageResult result) {
        return Map.of(
                TimingAttributeKeys.MEMBERS, Integer.toString(result.members().size()),
                TimingAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                TimingAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
    }

    static Map<String, String> packagePlan(PackagePlan plan) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(TimingAttributeKeys.MODE, plan.mode().configValue());
        attributes.put(TimingAttributeKeys.DEPENDENCIES, String.valueOf(plan.dependencies().size()));
        attributes.put(TimingAttributeKeys.WARNINGS, String.valueOf(plan.warnings().size()));
        return attributes;
    }
}
