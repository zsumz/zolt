package com.zolt.build;

import com.zolt.classpath.ClasspathSet;

public record TestCompileResultWithClasspaths(
        TestCompileResult testCompileResult,
        ClasspathSet classpaths) {
}
