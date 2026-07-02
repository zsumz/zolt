package sh.zolt.resolve.version;

import sh.zolt.dependency.ConflictSelectionReason;
import sh.zolt.dependency.PackageId;
import sh.zolt.resolve.request.DependencyRequest;
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
