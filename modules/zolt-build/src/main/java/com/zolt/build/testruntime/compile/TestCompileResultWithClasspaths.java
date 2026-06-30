package com.zolt.build.testruntime.compile;

import com.zolt.classpath.ClasspathSet;

public record TestCompileResultWithClasspaths(
        TestCompileResult testCompileResult,
        ClasspathSet classpaths) {
}
