package com.zolt.build;

import com.zolt.classpath.ClasspathSet;

record BuildResultWithClasspaths(
        BuildResult buildResult,
        ClasspathSet classpaths) {
}
