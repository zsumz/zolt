package com.zolt.lockfile;

import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.VersionComparator;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

public final class ZoltLockfileWriter {
    private final VersionComparator versionComparator = new VersionComparator();

    public void write(Path path, ZoltLockfile lockfile) {
        try {
            Files.writeString(path, write(lockfile));
        } catch (IOException exception) {
            throw new LockfileWriteException(
                    "Could not write zolt.lock at " + path + ". Check that the directory exists and is writable.",
                    exception);
        }
    }

    public String write(ZoltLockfile lockfile) {
        StringBuilder output = new StringBuilder();
        output.append("version = ").append(lockfile.version()).append("\n\n");
        for (LockPackage lockPackage : sortedPackages(lockfile.packages())) {
            writePackage(output, lockPackage);
        }
        for (LockConflict conflict : sortedConflicts(lockfile.conflicts())) {
            writeConflict(output, conflict);
        }
        return output.toString();
    }

    private void writePackage(StringBuilder output, LockPackage lockPackage) {
        output.append("[[package]]\n");
        assignment(output, "id", lockPackage.packageId().toString());
        assignment(output, "version", lockPackage.version());
        assignment(output, "source", lockPackage.source());
        assignment(output, "scope", lockPackage.scope().lockfileName());
        output.append("direct = ").append(lockPackage.direct()).append('\n');
        lockPackage.jar().ifPresent(value -> assignment(output, "jar", value));
        lockPackage.pom().ifPresent(value -> assignment(output, "pom", value));
        lockPackage.jarSha256().ifPresent(value -> assignment(output, "jarSha256", value));
        lockPackage.pomSha256().ifPresent(value -> assignment(output, "pomSha256", value));
        lockPackage.artifact().ifPresent(value -> assignment(output, "artifact", value));
        lockPackage.artifactType().ifPresent(value -> assignment(output, "artifactType", value));
        lockPackage.artifactSha256().ifPresent(value -> assignment(output, "artifactSha256", value));
        lockPackage.workspace().ifPresent(value -> assignment(output, "workspace", value));
        lockPackage.workspaceOutput().ifPresent(value -> assignment(output, "workspaceOutput", value));
        if (!lockPackage.members().isEmpty()) {
            output.append("members = ");
            stringArray(output, sortedStrings(lockPackage.members()));
            output.append('\n');
        }
        if (!lockPackage.exportedBy().isEmpty()) {
            output.append("exportedBy = ");
            stringArray(output, sortedStrings(lockPackage.exportedBy()));
            output.append('\n');
        }
        if (!lockPackage.policies().isEmpty()) {
            output.append("policies = ");
            stringArray(output, sortedStrings(lockPackage.policies()));
            output.append('\n');
        }
        output.append("dependencies = ");
        stringArray(output, sortedStrings(lockPackage.dependencies()));
        output.append("\n\n");
    }

    private void writeConflict(StringBuilder output, LockConflict conflict) {
        output.append("[[conflict]]\n");
        assignment(output, "id", conflict.packageId().toString());
        assignment(output, "selected", conflict.selectedVersion());
        output.append("requested = ");
        stringArray(output, sortedVersions(conflict.requestedVersions()));
        output.append('\n');
        assignment(output, "reason", reason(conflict.reason()));
        output.append('\n');
    }

    private static void assignment(StringBuilder output, String key, String value) {
        output.append(key).append(" = ").append(quote(value)).append('\n');
    }

    private static void stringArray(StringBuilder output, List<String> values) {
        output.append('[');
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                output.append(", ");
            }
            output.append(quote(values.get(index)));
        }
        output.append(']');
    }

    private static List<LockPackage> sortedPackages(List<LockPackage> packages) {
        return packages.stream()
                .sorted(Comparator.comparing(lockPackage ->
                        lockPackage.packageId() + ":" + lockPackage.version() + ":" + lockPackage.scope().lockfileName()))
                .toList();
    }

    private static List<LockConflict> sortedConflicts(List<LockConflict> conflicts) {
        return conflicts.stream()
                .sorted(Comparator.comparing(conflict -> conflict.packageId().toString()))
                .toList();
    }

    private static List<String> sortedStrings(List<String> values) {
        return values.stream().sorted().toList();
    }

    private List<String> sortedVersions(List<String> values) {
        return values.stream()
                .sorted(versionComparator.thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static String reason(ConflictSelectionReason reason) {
        return switch (reason) {
            case DIRECT_DEPENDENCY -> "direct dependency wins";
            case NEWEST_VERSION -> "newest version wins";
        };
    }

    private static String quote(String value) {
        return "\"" + value
                .replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t")
                + "\"";
    }
}
