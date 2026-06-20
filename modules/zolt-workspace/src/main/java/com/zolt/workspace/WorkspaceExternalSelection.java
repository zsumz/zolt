package com.zolt.workspace;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.lockfile.LockConflict;
import com.zolt.lockfile.LockPackage;
import java.util.List;

record WorkspaceExternalSelection(
        List<LockPackage> packages,
        List<LockConflict> conflicts) {
    WorkspaceExternalSelection {
        packages = List.copyOf(packages);
        conflicts = List.copyOf(conflicts);
    }

    record VersionSelection(
            String version,
            ConflictSelectionReason reason) {
    }
}
