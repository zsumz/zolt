package com.zolt.resolve;

import java.nio.file.Path;

public record ResolvedPackage(
        PackageId packageId,
        String selectedVersion,
        boolean direct,
        Path pomPath,
        Path jarPath) {
}
