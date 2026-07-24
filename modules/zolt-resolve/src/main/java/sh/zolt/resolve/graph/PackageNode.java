package sh.zolt.resolve.graph;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;

public record PackageNode(
        PackageId packageId,
        String selectedVersion,
        LockArtifactVariant variant) {
    public PackageNode(PackageId packageId, String selectedVersion) {
        this(packageId, selectedVersion, LockArtifactVariant.defaultVariant());
    }

    public PackageNode {
        variant = variant == null ? LockArtifactVariant.defaultVariant() : variant;
    }
}
