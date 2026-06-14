package com.zolt.resolve;

import com.zolt.dependency.DependencyScope;

public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope) {
}
