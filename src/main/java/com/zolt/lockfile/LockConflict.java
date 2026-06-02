package com.zolt.lockfile;

import com.zolt.resolve.ConflictSelectionReason;
import com.zolt.resolve.PackageId;
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
