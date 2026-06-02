package com.zolt.resolve;

public enum DependencyScope {
    COMPILE(true, true, false, true),
    RUNTIME(false, true, false, true),
    TEST(false, false, true, false),
    PROVIDED(true, false, false, false);

    private final boolean mainCompileClasspath;
    private final boolean mainRuntimeClasspath;
    private final boolean testClasspath;
    private final boolean packagedByDefault;

    DependencyScope(
            boolean mainCompileClasspath,
            boolean mainRuntimeClasspath,
            boolean testClasspath,
            boolean packagedByDefault) {
        this.mainCompileClasspath = mainCompileClasspath;
        this.mainRuntimeClasspath = mainRuntimeClasspath;
        this.testClasspath = testClasspath;
        this.packagedByDefault = packagedByDefault;
    }

    public boolean entersMainCompileClasspath() {
        return mainCompileClasspath;
    }

    public boolean entersMainRuntimeClasspath() {
        return mainRuntimeClasspath;
    }

    public boolean entersTestClasspath() {
        return testClasspath;
    }

    public boolean packagedByDefault() {
        return packagedByDefault;
    }
}
