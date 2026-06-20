package com.zolt.classpath;

import com.zolt.dependency.PackageId;
import java.nio.file.Path;

public record ResolvedPackage(
        PackageId packageId,
        String selectedVersion,
        boolean direct,
        Path pomPath,
        Path jarPath) {
}
