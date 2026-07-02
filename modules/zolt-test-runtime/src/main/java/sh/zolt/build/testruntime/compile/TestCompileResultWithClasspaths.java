package sh.zolt.build.testruntime.compile;

import sh.zolt.classpath.ClasspathSet;

public record TestCompileResultWithClasspaths(
        TestCompileResult testCompileResult,
        ClasspathSet classpaths) {
}
