package com.zolt.build;

import com.zolt.classpath.ClasspathSet;
import com.zolt.resolve.ResolvedClasspathPackage;
import java.util.List;

public record BuildResultWithClasspaths(
        BuildResult buildResult,
        ClasspathSet classpaths,
        List<ResolvedClasspathPackage> classpathPackages) {
    public BuildResultWithClasspaths {
        classpathPackages = List.copyOf(classpathPackages);
    }
}
