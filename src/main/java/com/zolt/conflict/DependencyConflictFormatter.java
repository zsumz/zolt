package com.zolt.conflict;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.resolve.VersionComparator;
import java.util.Comparator;
import java.util.List;

public final class DependencyConflictFormatter {
    private final VersionComparator versionComparator = new VersionComparator();

    public String format(ZoltLockfile lockfile) {
        if (lockfile.conflicts().isEmpty()) {
            return "No dependency conflicts found.\n";
        }

        StringBuilder output = new StringBuilder("Dependency conflicts:\n");
        for (LockConflict conflict : sortedConflicts(lockfile.conflicts())) {
            output.append("- ")
                    .append(conflict.packageId())
                    .append('\n')
                    .append("  selected: ")
                    .append(conflict.selectedVersion())
                    .append('\n')
                    .append("  requested: ")
                    .append(String.join(", ", sortedVersions(conflict.requestedVersions())))
                    .append('\n')
                    .append("  reason: ")
                    .append(reason(conflict.reason()))
                    .append('\n');
        }
        return output.toString();
    }

    private static List<LockConflict> sortedConflicts(List<LockConflict> conflicts) {
        return conflicts.stream()
                .sorted(Comparator.comparing(conflict -> conflict.packageId().toString()))
                .toList();
    }

    private List<String> sortedVersions(List<String> versions) {
        return versions.stream()
                .sorted(versionComparator.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static String reason(ConflictSelectionReason reason) {
        return switch (reason) {
            case DIRECT_DEPENDENCY -> "direct dependency wins";
            case NEWEST_VERSION -> "newest version wins";
        };
    }
}
