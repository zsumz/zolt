package com.zolt.lockfile;

import com.zolt.dependency.ConflictSelectionReason;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlInvalidTypeException;
import org.tomlj.TomlParseError;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

public final class ZoltLockfileReader {
    private final LockfilePackageCodec packageCodec;

    public ZoltLockfileReader() {
        this(new LockfilePackageCodec());
    }

    ZoltLockfileReader(LockfilePackageCodec packageCodec) {
        this.packageCodec = packageCodec;
    }

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

    private ZoltLockfile read(TomlParseResult result) {
        try {
            if (result.hasErrors()) {
                TomlParseError error = result.errors().getFirst();
                throw new LockfileReadException(
                        "Could not parse zolt.lock. Fix the TOML syntax near "
                                + error.position()
                                + ": "
                                + error.getMessage());
            }

            int version = LockfileTomlValues.requireInt(result, "version");
            if (version != ZoltLockfile.CURRENT_VERSION) {
                throw new LockfileReadException(
                        "Unsupported zolt.lock version "
                                + version
                                + ". Run `zolt resolve` with a compatible Zolt version to regenerate the lockfile.");
            }

            return new ZoltLockfile(
                    version,
                    LockfileTomlValues.optionalString(result, "aliasFingerprint"),
                    LockfileTomlValues.optionalString(result, "projectResolutionFingerprint"),
                    LockfileTomlValues.optionalStringArray(result, "projectResolutionInputFingerprints"),
                    packageCodec.packages(result.getArray("package")),
                    conflicts(result.getArray("conflict")),
                    policyEffects(result.getArray("policy")));
        } catch (TomlInvalidTypeException exception) {
            throw new LockfileReadException(
                    "Invalid value type in zolt.lock: "
                            + exception.getMessage()
                            + ". Run `zolt resolve` to regenerate the lockfile.",
                    exception);
        }
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
                    LockfileTomlValues.packageId(LockfileTomlValues.requireString(table, "id")),
                    LockfileTomlValues.requireString(table, "selected"),
                    LockfileTomlValues.stringArray(table, "requested"),
                    reason(LockfileTomlValues.requireString(table, "reason"))));
        }
        return conflicts;
    }

    private static List<LockPolicyEffect> policyEffects(TomlArray policyArray) {
        if (policyArray == null) {
            return List.of();
        }

        List<LockPolicyEffect> policyEffects = new ArrayList<>();
        for (int index = 0; index < policyArray.size(); index++) {
            TomlTable table = policyArray.getTable(index);
            if (table == null) {
                throw new LockfileReadException("Invalid policy entry at index " + index + " in zolt.lock.");
            }
            policyEffects.add(new LockPolicyEffect(
                    LockfileTomlValues.requireString(table, "kind"),
                    LockfileTomlValues.packageId(LockfileTomlValues.requireString(table, "id")),
                    LockfileTomlValues.optionalString(table, "requested"),
                    LockfileTomlValues.optionalString(table, "source"),
                    LockfileTomlValues.requireString(table, "policy")));
        }
        return policyEffects;
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
