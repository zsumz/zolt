package sh.zolt.lockfile;

import sh.zolt.dependency.PackageId;
import sh.zolt.dependency.ConflictSelectionReason;
import java.util.List;

public record LockConflict(
        PackageId packageId,
        String selectedVersion,
        List<String> requestedVersions,
        ConflictSelectionReason reason) {
    public LockConflict {
        requestedVersions = List.copyOf(requestedVersions);
    }
}
