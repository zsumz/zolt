package com.zolt.tree;

import com.zolt.lockfile.LockPackage;
import com.zolt.lockfile.ZoltLockfile;
import com.zolt.project.ProjectConfig;
import com.zolt.resolve.PackageId;
import java.util.ArrayDeque;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class DependencyWhyFormatter {
    public String format(ProjectConfig config, ZoltLockfile lockfile, PackageId target) {
        List<LockPackage> path = pathTo(lockfile, target).orElseThrow(() -> new DependencyWhyException(
                "Package " + target + " is not present in zolt.lock. Run `zolt resolve` after adding it or check the package id."));
        StringBuilder output = new StringBuilder();
        output.append(config.project().group())
                .append(':')
                .append(config.project().name())
                .append(':')
                .append(config.project().version())
                .append('\n');
        for (int index = 0; index < path.size(); index++) {
            output.append("   ".repeat(index))
                    .append("\\- ")
                    .append(path.get(index).packageId())
                    .append(':')
                    .append(path.get(index).version())
                    .append('\n');
        }
        return output.toString();
    }

    private static Optional<List<LockPackage>> pathTo(ZoltLockfile lockfile, PackageId target) {
        Map<String, LockPackage> packages = packagesByCoordinate(lockfile);
        ArrayDeque<PathItem> queue = new ArrayDeque<>();
        lockfile.packages().stream()
                .filter(LockPackage::direct)
                .sorted(Comparator.comparing(DependencyWhyFormatter::coordinate))
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
                .sorted(Comparator.comparing(DependencyWhyFormatter::coordinate))
                .forEach(lockPackage -> packages.put(coordinate(lockPackage), lockPackage));
        return packages;
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private static boolean contains(List<LockPackage> path, LockPackage candidate) {
        return path.stream().anyMatch(lockPackage -> coordinate(lockPackage).equals(coordinate(candidate)));
    }

    private static List<LockPackage> append(List<LockPackage> path, LockPackage lockPackage) {
        java.util.ArrayList<LockPackage> updated = new java.util.ArrayList<>(path);
        updated.add(lockPackage);
        return List.copyOf(updated);
    }

    private record PathItem(LockPackage lockPackage, List<LockPackage> path) {
        private PathItem {
            path = List.copyOf(path);
        }
    }
}
