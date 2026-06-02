package com.zolt.lockfile;

import java.util.List;

public record ZoltLockfile(
        int version,
        List<LockPackage> packages,
        List<LockConflict> conflicts) {
    public static final int CURRENT_VERSION = 1;

    public ZoltLockfile {
        packages = List.copyOf(packages);
        conflicts = List.copyOf(conflicts);
    }
}
