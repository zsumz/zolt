package com.zolt.build;

import com.zolt.classpath.ClasspathSet;

record TestCompileResultWithClasspaths(
        TestCompileResult testCompileResult,
        ClasspathSet classpaths) {
}
