package sh.zolt.classpath;

import sh.zolt.dependency.DependencyScope;

public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope) {
}
