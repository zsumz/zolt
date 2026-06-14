package com.zolt.classpath;

import com.zolt.dependency.DependencyScope;
import com.zolt.resolve.ResolvedPackage;

public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope) {
}
