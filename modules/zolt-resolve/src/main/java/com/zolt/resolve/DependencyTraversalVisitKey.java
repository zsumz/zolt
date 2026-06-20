package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;
import com.zolt.dependency.PackageId;

record DependencyTraversalVisitKey(PackageId packageId, String version, DependencyScope scope)
        implements Comparable<DependencyTraversalVisitKey> {
    static DependencyTraversalVisitKey from(PackageNode node, DependencyScope scope) {
        return new DependencyTraversalVisitKey(node.packageId(), node.selectedVersion(), scope);
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
        return scope.compareTo(other.scope);
    }
}
