package sh.zolt.workspace.resolve;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.lockfile.LockConflict;
import sh.zolt.lockfile.LockPackage;
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
