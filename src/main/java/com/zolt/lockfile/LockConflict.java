package com.zolt.lockfile;

import com.zolt.dependency.PackageId;
import com.zolt.dependency.ConflictSelectionReason;
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
