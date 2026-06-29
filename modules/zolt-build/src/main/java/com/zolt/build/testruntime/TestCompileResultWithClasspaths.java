package com.zolt.build.testruntime;

import com.zolt.classpath.ClasspathSet;

public record TestCompileResultWithClasspaths(
        TestCompileResult testCompileResult,
        ClasspathSet classpaths) {
}
