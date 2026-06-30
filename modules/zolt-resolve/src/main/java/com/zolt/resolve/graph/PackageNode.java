package com.zolt.resolve.graph;

import com.zolt.dependency.PackageId;

public record PackageNode(
        PackageId packageId,
        String selectedVersion) {
}
