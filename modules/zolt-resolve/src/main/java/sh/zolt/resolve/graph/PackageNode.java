package sh.zolt.resolve.graph;

import sh.zolt.dependency.PackageId;

public record PackageNode(
        PackageId packageId,
        String selectedVersion) {
}
