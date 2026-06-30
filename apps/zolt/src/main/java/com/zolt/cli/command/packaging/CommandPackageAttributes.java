package com.zolt.cli.command.packaging;

import com.zolt.build.packageplan.PackagePlan;
import com.zolt.build.packaging.PackageResult;
import com.zolt.cli.command.CommandAttributeKeys;
import com.zolt.workspace.packaging.WorkspacePackageResult;
import java.util.LinkedHashMap;
import java.util.Map;

final class CommandPackageAttributes {
    private CommandPackageAttributes() {
    }

    static Map<String, String> packageResult(PackageResult result) {
        return Map.of(
                CommandAttributeKeys.MODE, result.mode().configValue(),
                CommandAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                CommandAttributeKeys.HAS_MAIN_CLASS, Boolean.toString(result.hasMainClass()),
                CommandAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.buildResult().resolvedLockfile()));
    }

    static Map<String, String> workspacePackage(WorkspacePackageResult result) {
        return Map.of(
                CommandAttributeKeys.MEMBERS, Integer.toString(result.members().size()),
                CommandAttributeKeys.ENTRIES, Integer.toString(result.entryCount()),
                CommandAttributeKeys.RESOLVED_LOCKFILE, Boolean.toString(result.resolvedLockfile()));
    }

    static Map<String, String> packagePlan(PackagePlan plan) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put(CommandAttributeKeys.MODE, plan.mode().configValue());
        attributes.put(CommandAttributeKeys.DEPENDENCIES, String.valueOf(plan.dependencies().size()));
        attributes.put(CommandAttributeKeys.WARNINGS, String.valueOf(plan.warnings().size()));
        return attributes;
    }
}
