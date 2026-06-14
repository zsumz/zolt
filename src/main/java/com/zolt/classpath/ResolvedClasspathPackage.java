package com.zolt.classpath;

import com.zolt.dependency.DependencyScope;

public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope) {
}
