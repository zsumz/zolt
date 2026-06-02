package com.zolt.classpath;

import com.zolt.resolve.DependencyScope;
import com.zolt.resolve.ResolvedPackage;

public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope) {
}
