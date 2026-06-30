package com.zolt.resolve.traversal;

import com.zolt.dependency.PackageId;
import com.zolt.resolve.graph.PackageNode;

record DependencyTraversalNodeKey(PackageId packageId, String version)
        implements Comparable<DependencyTraversalNodeKey> {
    static DependencyTraversalNodeKey from(PackageNode node) {
        return new DependencyTraversalNodeKey(node.packageId(), node.selectedVersion());
    }

    @Override
    public int compareTo(DependencyTraversalNodeKey other) {
        int packageCompared = packageId.toString().compareTo(other.packageId.toString());
        if (packageCompared != 0) {
            return packageCompared;
        }
        return version.compareTo(other.version);
    }
}
