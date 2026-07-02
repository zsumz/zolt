package sh.zolt.build;

import sh.zolt.classpath.ClasspathSet;
import sh.zolt.classpath.ResolvedClasspathPackage;
import java.util.List;

public record BuildResultWithClasspaths(
        BuildResult buildResult,
        ClasspathSet classpaths,
        List<ResolvedClasspathPackage> classpathPackages) {
    public BuildResultWithClasspaths {
        classpathPackages = List.copyOf(classpathPackages);
    }
}
