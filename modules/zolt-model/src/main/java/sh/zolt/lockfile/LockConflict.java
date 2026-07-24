package sh.zolt.lockfile;

import sh.zolt.dependency.PackageId;
import sh.zolt.dependency.ConflictSelectionReason;
import java.util.List;
import java.util.Optional;

/**
 * A recorded version mediation. {@code toolGroup} names the isolated exec-tool closure whose own
 * resolution mediated this conflict (see Hole 1); it is empty for a mediation in the main project graph.
 * The attribution keeps the audit trail unambiguous about WHICH closure conflicted when the same GA
 * mediates in more than one place.
 */
public record LockConflict(
        PackageId packageId,
        String selectedVersion,
        List<String> requestedVersions,
        ConflictSelectionReason reason,
        Optional<String> toolGroup) {
    public LockConflict {
        requestedVersions = List.copyOf(requestedVersions);
        toolGroup = toolGroup == null ? Optional.empty() : toolGroup;
    }

    public LockConflict(
            PackageId packageId,
            String selectedVersion,
            List<String> requestedVersions,
            ConflictSelectionReason reason) {
        this(packageId, selectedVersion, requestedVersions, reason, Optional.empty());
    }
}
