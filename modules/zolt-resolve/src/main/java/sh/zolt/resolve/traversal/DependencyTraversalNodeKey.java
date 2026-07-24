package sh.zolt.resolve.traversal;

import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.resolve.graph.PackageNode;

record DependencyTraversalNodeKey(PackageId packageId, String version, LockArtifactVariant variant)
        implements Comparable<DependencyTraversalNodeKey> {
    DependencyTraversalNodeKey(PackageId packageId, String version) {
        this(packageId, version, LockArtifactVariant.defaultVariant());
    }

    static DependencyTraversalNodeKey from(PackageNode node) {
        return new DependencyTraversalNodeKey(node.packageId(), node.selectedVersion(), node.variant());
    }

    @Override
    public int compareTo(DependencyTraversalNodeKey other) {
        int packageCompared = packageId.toString().compareTo(other.packageId.toString());
        if (packageCompared != 0) {
            return packageCompared;
        }
        int versionCompared = version.compareTo(other.version);
        if (versionCompared != 0) {
            return versionCompared;
        }
        return variant.compareTo(other.variant);
    }
}
