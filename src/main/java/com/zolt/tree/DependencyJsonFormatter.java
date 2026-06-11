package com.zolt.tree;

import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.LockPolicyEffect;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.PackageId;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DependencyJsonFormatter {
    public String tree(ProjectConfig config, ZoltLockfile lockfile) {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "command", "tree", true);
        project(json, config);
        comma(json);
        packages(json, lockfile.packages());
        comma(json);
        stringArrayField(json, 1, "roots", roots(lockfile), true);
        conflicts(json, lockfile.conflicts());
        comma(json);
        policyEffects(json, lockfile.policyEffects());
        json.append("\n}\n");
        return json.toString();
    }

    public String why(ProjectConfig config, ZoltLockfile lockfile, PackageId target) {
        Optional<List<LockPackage>> path = pathTo(lockfile, target);
        List<LockPolicyEffect> effects = exclusionEffects(lockfile, target);
        if (path.isEmpty() && effects.isEmpty()) {
            throw new DependencyWhyException(
                    "Package " + target + " is not present in zolt.lock. Run `zolt resolve` after adding it or check the package id.");
        }

        StringBuilder json = new StringBuilder();
        json.append("{\n");
        field(json, 1, "schemaVersion", 1, true);
        stringField(json, 1, "command", "why", true);
        project(json, config);
        comma(json);
        stringField(json, 1, "target", target.toString(), true);
        stringField(json, 1, "status", path.isPresent() ? "present" : "excluded", true);
        path(json, path.orElse(List.of()));
        comma(json);
        policyEffects(json, effects);
        json.append("\n}\n");
        return json.toString();
    }

    private static void project(StringBuilder json, ProjectConfig config) {
        indent(json, 1).append("\"project\": {\n");
        stringField(json, 2, "group", config.project().group(), true);
        stringField(json, 2, "name", config.project().name(), true);
        stringField(json, 2, "version", config.project().version(), true);
        stringField(json, 2, "coordinate", projectCoordinate(config), false);
        indent(json, 1).append("}");
    }

    private static void packages(StringBuilder json, List<LockPackage> packages) {
        indent(json, 1).append("\"packages\": [");
        List<LockPackage> sorted = packages.stream()
                .sorted(Comparator.comparing(DependencyJsonFormatter::coordinate))
                .toList();
        if (!sorted.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < sorted.size(); index++) {
                packageObject(json, 2, sorted.get(index));
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void packageObject(StringBuilder json, int level, LockPackage lockPackage) {
        indent(json, level).append("{\n");
        stringField(json, level + 1, "id", lockPackage.packageId().toString(), true);
        stringField(json, level + 1, "version", lockPackage.version(), true);
        stringField(json, level + 1, "coordinate", coordinate(lockPackage), true);
        stringField(json, level + 1, "scope", lockPackage.scope().lockfileName(), true);
        booleanField(json, level + 1, "direct", lockPackage.direct(), true);
        stringArrayField(json, level + 1, "dependencies", lockPackage.dependencies().stream().sorted().toList(), true);
        stringArrayField(json, level + 1, "policies", lockPackage.policies().stream().sorted().toList(), false);
        indent(json, level).append("}");
    }

    private static void path(StringBuilder json, List<LockPackage> path) {
        indent(json, 1).append("\"path\": [");
        if (!path.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < path.size(); index++) {
                packageObject(json, 2, path.get(index));
                if (index + 1 < path.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void conflicts(StringBuilder json, List<LockConflict> conflicts) {
        indent(json, 1).append("\"conflicts\": [");
        List<LockConflict> sorted = conflicts.stream()
                .sorted(Comparator.comparing(conflict -> conflict.packageId().toString()))
                .toList();
        if (!sorted.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < sorted.size(); index++) {
                LockConflict conflict = sorted.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "id", conflict.packageId().toString(), true);
                stringField(json, 3, "selected", conflict.selectedVersion(), true);
                stringArrayField(json, 3, "requested", conflict.requestedVersions().stream().sorted().toList(), true);
                stringField(json, 3, "reason", reason(conflict.reason()), false);
                indent(json, 2).append("}");
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static void policyEffects(StringBuilder json, List<LockPolicyEffect> policyEffects) {
        indent(json, 1).append("\"policyEffects\": [");
        List<LockPolicyEffect> sorted = policyEffects.stream()
                .sorted(Comparator.comparing(DependencyJsonFormatter::policyEffectSortKey))
                .toList();
        if (!sorted.isEmpty()) {
            json.append('\n');
            for (int index = 0; index < sorted.size(); index++) {
                LockPolicyEffect effect = sorted.get(index);
                indent(json, 2).append("{\n");
                stringField(json, 3, "kind", effect.kind(), true);
                stringField(json, 3, "id", effect.packageId().toString(), true);
                optionalStringField(json, 3, "requested", effect.requestedVersion(), true);
                optionalStringField(json, 3, "source", effect.source(), true);
                stringField(json, 3, "policy", effect.policy(), false);
                indent(json, 2).append("}");
                if (index + 1 < sorted.size()) {
                    json.append(',');
                }
                json.append('\n');
            }
            indent(json, 1);
        }
        json.append("]");
    }

    private static List<String> roots(ZoltLockfile lockfile) {
        return lockfile.packages().stream()
                .filter(LockPackage::direct)
                .map(DependencyJsonFormatter::coordinate)
                .sorted()
                .toList();
    }

    private static Optional<List<LockPackage>> pathTo(ZoltLockfile lockfile, PackageId target) {
        Map<String, LockPackage> packages = packagesByCoordinate(lockfile);
        ArrayDeque<PathItem> queue = new ArrayDeque<>();
        lockfile.packages().stream()
                .filter(LockPackage::direct)
                .sorted(Comparator.comparing(DependencyJsonFormatter::coordinate))
                .forEach(lockPackage -> queue.add(new PathItem(lockPackage, List.of(lockPackage))));

        while (!queue.isEmpty()) {
            PathItem item = queue.removeFirst();
            if (item.lockPackage().packageId().equals(target)) {
                return Optional.of(item.path());
            }
            item.lockPackage().dependencies().stream()
                    .sorted()
                    .map(packages::get)
                    .filter(java.util.Objects::nonNull)
                    .filter(dependency -> !contains(item.path(), dependency))
                    .forEach(dependency -> queue.add(new PathItem(
                            dependency,
                            append(item.path(), dependency))));
        }
        return Optional.empty();
    }

    private static Map<String, LockPackage> packagesByCoordinate(ZoltLockfile lockfile) {
        Map<String, LockPackage> packages = new LinkedHashMap<>();
        lockfile.packages().stream()
                .sorted(Comparator.comparing(DependencyJsonFormatter::coordinate))
                .forEach(lockPackage -> packages.put(coordinate(lockPackage), lockPackage));
        return packages;
    }

    private static List<LockPolicyEffect> exclusionEffects(ZoltLockfile lockfile, PackageId target) {
        return lockfile.policyEffects().stream()
                .filter(effect -> effect.packageId().equals(target))
                .filter(DependencyJsonFormatter::exclusion)
                .sorted(Comparator.comparing(DependencyJsonFormatter::policyEffectSortKey))
                .toList();
    }

    private static boolean exclusion(LockPolicyEffect effect) {
        return "global-exclusion".equals(effect.kind()) || "edge-exclusion".equals(effect.kind());
    }

    private static boolean contains(List<LockPackage> path, LockPackage candidate) {
        return path.stream().anyMatch(lockPackage -> coordinate(lockPackage).equals(coordinate(candidate)));
    }

    private static List<LockPackage> append(List<LockPackage> path, LockPackage lockPackage) {
        java.util.ArrayList<LockPackage> updated = new java.util.ArrayList<>(path);
        updated.add(lockPackage);
        return List.copyOf(updated);
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private static String projectCoordinate(ProjectConfig config) {
        return config.project().group() + ":" + config.project().name() + ":" + config.project().version();
    }

    private static String policyEffectSortKey(LockPolicyEffect effect) {
        return effect.kind()
                + ":"
                + effect.packageId()
                + ":"
                + effect.requestedVersion().orElse("")
                + ":"
                + effect.source().orElse("")
                + ":"
                + effect.policy();
    }

    private static String reason(ConflictSelectionReason reason) {
        return switch (reason) {
            case DIRECT_DEPENDENCY -> "direct dependency wins";
            case NEWEST_VERSION -> "newest version wins";
        };
    }

    private static void stringArrayField(
            StringBuilder json,
            int level,
            String name,
            List<String> values,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": [");
        for (int index = 0; index < values.size(); index++) {
            if (index > 0) {
                json.append(", ");
            }
            string(json, values.get(index));
        }
        json.append("]");
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void stringField(StringBuilder json, int level, String name, String value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        string(json, value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void optionalStringField(
            StringBuilder json,
            int level,
            String name,
            Optional<String> value,
            boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ");
        value.ifPresentOrElse(
                present -> string(json, present),
                () -> json.append("null"));
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void booleanField(StringBuilder json, int level, String name, boolean value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void field(StringBuilder json, int level, String name, int value, boolean trailingComma) {
        indent(json, level);
        string(json, name);
        json.append(": ").append(value);
        if (trailingComma) {
            json.append(',');
        }
        json.append('\n');
    }

    private static void string(StringBuilder json, String value) {
        json.append('"');
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            switch (character) {
                case '"' -> json.append("\\\"");
                case '\\' -> json.append("\\\\");
                case '\b' -> json.append("\\b");
                case '\f' -> json.append("\\f");
                case '\n' -> json.append("\\n");
                case '\r' -> json.append("\\r");
                case '\t' -> json.append("\\t");
                default -> {
                    if (character < 0x20) {
                        json.append(String.format("\\u%04x", (int) character));
                    } else {
                        json.append(character);
                    }
                }
            }
        }
        json.append('"');
    }

    private static StringBuilder indent(StringBuilder json, int level) {
        return json.append("  ".repeat(level));
    }

    private static void comma(StringBuilder json) {
        json.append(",\n");
    }

    private record PathItem(LockPackage lockPackage, List<LockPackage> path) {
        private PathItem {
            path = List.copyOf(path);
        }
    }
}
