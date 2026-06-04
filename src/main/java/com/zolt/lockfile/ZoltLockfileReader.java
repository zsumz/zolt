package com.zolt.lockfile;

import com.zolt.classpath.ResolvedClasspathPackage;
import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.PackageId;
import com.zolt.resolve.ResolvedPackage;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class ZoltLockfileReader {
    public ZoltLockfile read(Path path) {
        try {
            return read(Toml.parse(path));
        } catch (IOException exception) {
            throw new LockfileReadException(
                    "Could not read zolt.lock at " + path + ". Check that the file exists and is readable.",
                    exception);
        }
    }

    public ZoltLockfile read(String content) {
        return read(Toml.parse(content));
    }

    public List<ResolvedClasspathPackage> classpathPackages(ZoltLockfile lockfile) {
        return classpathPackages(lockfile, Path.of(""));
    }

    public List<ResolvedClasspathPackage> classpathPackages(ZoltLockfile lockfile, Path cacheRoot) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.jar().isPresent())
                .map(lockPackage -> new ResolvedClasspathPackage(
                        new ResolvedPackage(
                                lockPackage.packageId(),
                                lockPackage.version(),
                                lockPackage.direct(),
                                lockPackage.pom().map(value -> cacheRoot.resolve(value)).orElse(Path.of("")),
                                cacheRoot.resolve(lockPackage.jar().orElseThrow())),
                        lockPackage.scope()))
                .toList();
    }

    public List<ResolvedClasspathPackage> classpathPackages(
            ZoltLockfile lockfile,
            Path cacheRoot,
            Path workspaceRoot) {
        return lockfile.packages().stream()
                .filter(lockPackage -> lockPackage.jar().isPresent()
                        || (lockPackage.workspace().isPresent() && lockPackage.workspaceOutput().isPresent()))
                .map(lockPackage -> {
                    Path classpathPath = lockPackage.workspace().isPresent()
                            ? workspaceRoot.resolve(lockPackage.workspace().orElseThrow())
                                    .resolve(lockPackage.workspaceOutput().orElseThrow())
                                    .normalize()
                            : cacheRoot.resolve(lockPackage.jar().orElseThrow());
                    return new ResolvedClasspathPackage(
                            new ResolvedPackage(
                                    lockPackage.packageId(),
                                    lockPackage.version(),
                                    lockPackage.direct(),
                                    lockPackage.pom().map(value -> cacheRoot.resolve(value)).orElse(Path.of("")),
                                    classpathPath),
                            lockPackage.scope());
                })
                .toList();
    }

    private ZoltLockfile read(TomlParseResult result) {
        if (result.hasErrors()) {
            TomlParseError error = result.errors().getFirst();
            throw new LockfileReadException(
                    "Could not parse zolt.lock. Fix the TOML syntax near "
                            + error.position()
                            + ": "
                            + error.getMessage());
        }

        int version = requireInt(result, "version");
        if (version != ZoltLockfile.CURRENT_VERSION) {
            throw new LockfileReadException(
                    "Unsupported zolt.lock version "
                            + version
                            + ". Run `zolt resolve` with a compatible Zolt version to regenerate the lockfile.");
        }

        return new ZoltLockfile(
                version,
                packages(result.getArray("package")),
                conflicts(result.getArray("conflict")));
    }

    private static List<LockPackage> packages(TomlArray packageArray) {
        if (packageArray == null) {
            return List.of();
        }

        List<LockPackage> packages = new ArrayList<>();
        for (int index = 0; index < packageArray.size(); index++) {
            TomlTable table = packageArray.getTable(index);
            if (table == null) {
                throw new LockfileReadException("Invalid package entry at index " + index + " in zolt.lock.");
            }
            packages.add(lockPackage(table));
        }
        return packages;
    }

    private static LockPackage lockPackage(TomlTable table) {
        PackageId packageId = packageId(requireString(table, "id"));
        return new LockPackage(
                packageId,
                requireString(table, "version"),
                requireString(table, "source"),
                scope(requireString(table, "scope"), packageId),
                requireBoolean(table, "direct"),
                optionalString(table, "jar"),
                optionalString(table, "pom"),
                optionalString(table, "jarSha256"),
                optionalString(table, "pomSha256"),
                optionalString(table, "workspace"),
                optionalString(table, "workspaceOutput"),
                stringArray(table, "dependencies"),
                optionalStringArray(table, "members"));
    }

    private static List<LockConflict> conflicts(TomlArray conflictArray) {
        if (conflictArray == null) {
            return List.of();
        }

        List<LockConflict> conflicts = new ArrayList<>();
        for (int index = 0; index < conflictArray.size(); index++) {
            TomlTable table = conflictArray.getTable(index);
            if (table == null) {
                throw new LockfileReadException("Invalid conflict entry at index " + index + " in zolt.lock.");
            }
            conflicts.add(new LockConflict(
                    packageId(requireString(table, "id")),
                    requireString(table, "selected"),
                    stringArray(table, "requested"),
                    reason(requireString(table, "reason"))));
        }
        return conflicts;
    }

    private static int requireInt(TomlTable table, String key) {
        Long value = table.getLong(key);
        if (value == null) {
            throw new LockfileReadException("Missing required integer field `" + key + "` in zolt.lock.");
        }
        return Math.toIntExact(value);
    }

    private static String requireString(TomlTable table, String key) {
        String value = table.getString(key);
        if (value == null || value.isBlank()) {
            throw new LockfileReadException("Missing required string field `" + key + "` in zolt.lock.");
        }
        return value;
    }

    private static boolean requireBoolean(TomlTable table, String key) {
        Boolean value = table.getBoolean(key);
        if (value == null) {
            throw new LockfileReadException("Missing required boolean field `" + key + "` in zolt.lock.");
        }
        return value;
    }

    private static Optional<String> optionalString(TomlTable table, String key) {
        String value = table.getString(key);
        return value == null || value.isBlank() ? Optional.empty() : Optional.of(value);
    }

    private static List<String> stringArray(TomlTable table, String key) {
        TomlArray array = table.getArray(key);
        if (array == null) {
            throw new LockfileReadException("Missing required string array field `" + key + "` in zolt.lock.");
        }

        return strings(array, key);
    }

    private static List<String> optionalStringArray(TomlTable table, String key) {
        TomlArray array = table.getArray(key);
        if (array == null) {
            return List.of();
        }

        return strings(array, key);
    }

    private static List<String> strings(TomlArray array, String key) {
        List<String> values = new ArrayList<>();
        for (int index = 0; index < array.size(); index++) {
            String value = array.getString(index);
            if (value == null || value.isBlank()) {
                throw new LockfileReadException(
                        "Invalid string value at `" + key + "[" + index + "]` in zolt.lock.");
            }
            values.add(value);
        }
        return values;
    }

    private static PackageId packageId(String value) {
        String[] parts = value.split(":", -1);
        if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) {
            throw new LockfileReadException(
                    "Invalid package id `" + value + "` in zolt.lock. Expected `group:artifact`.");
        }
        return new PackageId(parts[0], parts[1]);
    }

    private static DependencyScope scope(String value, PackageId packageId) {
        for (DependencyScope scope : DependencyScope.values()) {
            if (scope.lockfileName().equals(value)) {
                return scope;
            }
        }
        try {
            return DependencyScope.valueOf(value.replace('-', '_').toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException exception) {
            throw new LockfileReadException(
                    "Invalid scope `" + value + "` for " + packageId + " in zolt.lock.",
                    exception);
        }
    }

    private static ConflictSelectionReason reason(String value) {
        return switch (value) {
            case "direct dependency wins" -> ConflictSelectionReason.DIRECT_DEPENDENCY;
            case "newest version wins" -> ConflictSelectionReason.NEWEST_VERSION;
            default -> throw new LockfileReadException(
                    "Invalid conflict reason `" + value + "` in zolt.lock.");
        };
    }
}
