package com.zolt.lockfile;

import java.util.List;

public record ZoltLockfile(
        int version,
        List<LockPackage> packages,
        List<LockConflict> conflicts,
        List<LockPolicyEffect> policyEffects) {
    public static final int CURRENT_VERSION = 1;

    public ZoltLockfile(
            int version,
            List<LockPackage> packages,
            List<LockConflict> conflicts) {
        this(version, packages, conflicts, List.of());
    }

    public ZoltLockfile {
        packages = List.copyOf(packages);
        conflicts = List.copyOf(conflicts);
        policyEffects = policyEffects == null ? List.of() : List.copyOf(policyEffects);
    }
}
