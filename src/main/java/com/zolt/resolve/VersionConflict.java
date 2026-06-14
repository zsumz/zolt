package com.zolt.resolve;

import com.zolt.dependency.PackageId;
import java.util.List;

public record VersionConflict(
        PackageId packageId,
        List<DependencyRequest> requests,
        String selectedVersion,
        ConflictSelectionReason selectionReason) {
    public VersionConflict {
        requests = List.copyOf(requests);
    }
}
