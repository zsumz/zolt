package sh.zolt.resolve.traversal;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.resolve.graph.PackageNode;

record DependencyTraversalVisitKey(
        PackageId packageId,
        String version,
        LockArtifactVariant variant,
        DependencyScope scope)
        implements Comparable<DependencyTraversalVisitKey> {
    static DependencyTraversalVisitKey from(PackageNode node, DependencyScope scope) {
        return new DependencyTraversalVisitKey(node.packageId(), node.selectedVersion(), node.variant(), scope);
    }

    @Override
    public int compareTo(DependencyTraversalVisitKey other) {
        int packageCompared = packageId.toString().compareTo(other.packageId.toString());
        if (packageCompared != 0) {
            return packageCompared;
        }
        int versionCompared = version.compareTo(other.version);
        if (versionCompared != 0) {
            return versionCompared;
        }
        int variantCompared = variant.compareTo(other.variant);
        if (variantCompared != 0) {
            return variantCompared;
        }
        return scope.compareTo(other.scope);
    }
}
