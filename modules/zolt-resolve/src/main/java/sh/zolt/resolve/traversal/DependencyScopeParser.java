package sh.zolt.resolve.traversal;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.maven.repository.RawPomDependency;

public final class DependencyScopeParser {
    public DependencyScope parse(RawPomDependency dependency) {
        String scope = dependency.scope().orElse("compile");
        return switch (scope) {
            case "compile" -> DependencyScope.COMPILE;
            case "runtime" -> DependencyScope.RUNTIME;
            case "test" -> DependencyScope.TEST;
            case "provided" -> DependencyScope.PROVIDED;
            default -> throw new DependencyScopeException(
                    "Unsupported dependency scope `"
                            + scope
                            + "` for "
                            + dependency.groupId()
                            + ":"
                            + dependency.artifactId()
                            + ". Supported scopes are compile, runtime, test, and provided.");
        };
    }
}
