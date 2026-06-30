package com.zolt.resolve.version;

import com.zolt.dependency.ConflictSelectionReason;
import com.zolt.dependency.PackageId;
import com.zolt.resolve.request.DependencyRequest;
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
