package com.zolt.resolve;

import com.zolt.dependency.PackageId;

public record PackageNode(
        PackageId packageId,
        String selectedVersion) {
}
