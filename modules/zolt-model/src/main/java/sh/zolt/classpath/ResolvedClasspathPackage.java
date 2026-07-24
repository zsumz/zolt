package sh.zolt.classpath;

import sh.zolt.dependency.DependencyScope;
import java.util.List;

/**
 * A resolved package on some classpath lane. For {@code tool-exec} packages {@code toolGroups} carries
 * the named exec tools whose locked closure this jar belongs to, so exec classpath construction can
 * select exactly the requesting tool's jars (never another tool's, even at a conflicting version).
 * Non-tool packages carry an empty list.
 */
public record ResolvedClasspathPackage(
        ResolvedPackage resolvedPackage,
        DependencyScope scope,
        List<String> toolGroups) {
    public ResolvedClasspathPackage {
        toolGroups = toolGroups == null ? List.of() : List.copyOf(toolGroups);
    }

    public ResolvedClasspathPackage(ResolvedPackage resolvedPackage, DependencyScope scope) {
        this(resolvedPackage, scope, List.of());
    }
}
