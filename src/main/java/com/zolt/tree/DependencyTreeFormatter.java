package com.zolt.tree;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ConflictSelectionReason;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class DependencyTreeFormatter {
    public String format(ProjectConfig config, ZoltLockfile lockfile) {
        Map<String, LockPackage> packages = packagesByCoordinate(lockfile);
        Map<String, LockConflict> conflicts = conflictsByPackage(lockfile);
        List<LockPackage> directPackages = lockfile.packages().stream()
                .filter(LockPackage::direct)
                .sorted(Comparator.comparing(DependencyTreeFormatter::coordinate))
                .toList();

        StringBuilder output = new StringBuilder();
        output.append(config.project().group())
                .append(':')
                .append(config.project().name())
                .append(':')
                .append(config.project().version())
                .append('\n');
        for (int index = 0; index < directPackages.size(); index++) {
            writePackage(
                    output,
                    directPackages.get(index),
                    packages,
                    conflicts,
                    "",
                    index == directPackages.size() - 1,
                    List.of());
        }
        return output.toString();
    }

    private static void writePackage(
            StringBuilder output,
            LockPackage lockPackage,
            Map<String, LockPackage> packages,
            Map<String, LockConflict> conflicts,
            String prefix,
            boolean last,
            List<String> ancestors) {
        String coordinate = coordinate(lockPackage);
        output.append(prefix).append(last ? "\\- " : "+- ").append(coordinate);
        LockConflict conflict = conflicts.get(lockPackage.packageId().toString());
        if (conflict != null) {
            output.append(" (conflict: selected ")
                    .append(conflict.selectedVersion())
                    .append("; requested ")
                    .append(String.join(", ", conflict.requestedVersions().stream().sorted().toList()))
                    .append("; ")
                    .append(reason(conflict.reason()))
                    .append(')');
        }
        appendPolicies(output, lockPackage);
        if (ancestors.contains(coordinate)) {
            output.append(" (cycle)");
        }
        output.append('\n');

        if (ancestors.contains(coordinate)) {
            return;
        }

        List<LockPackage> dependencies = lockPackage.dependencies().stream()
                .sorted()
                .map(packages::get)
                .filter(java.util.Objects::nonNull)
                .toList();
        String childPrefix = prefix + (last ? "   " : "|  ");
        List<String> nextAncestors = new ArrayList<>(ancestors);
        nextAncestors.add(coordinate);
        for (int index = 0; index < dependencies.size(); index++) {
            writePackage(
                    output,
                    dependencies.get(index),
                    packages,
                    conflicts,
                    childPrefix,
                    index == dependencies.size() - 1,
                    nextAncestors);
        }
    }

    private static Map<String, LockPackage> packagesByCoordinate(ZoltLockfile lockfile) {
        Map<String, LockPackage> packages = new LinkedHashMap<>();
        lockfile.packages().stream()
                .sorted(Comparator.comparing(DependencyTreeFormatter::coordinate))
                .forEach(lockPackage -> packages.put(coordinate(lockPackage), lockPackage));
        return packages;
    }

    private static Map<String, LockConflict> conflictsByPackage(ZoltLockfile lockfile) {
        Map<String, LockConflict> conflicts = new LinkedHashMap<>();
        lockfile.conflicts().stream()
                .sorted(Comparator.comparing(conflict -> conflict.packageId().toString()))
                .forEach(conflict -> conflicts.put(conflict.packageId().toString(), conflict));
        return conflicts;
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private static String reason(ConflictSelectionReason reason) {
        return switch (reason) {
            case DIRECT_DEPENDENCY -> "direct dependency wins";
            case NEWEST_VERSION -> "newest version wins";
        };
    }

    private static void appendPolicies(StringBuilder output, LockPackage lockPackage) {
        if (lockPackage.policies().isEmpty()) {
            return;
        }
        output.append(" (policy: ")
                .append(String.join("; ", lockPackage.policies().stream().sorted().toList()))
                .append(')');
    }
}
