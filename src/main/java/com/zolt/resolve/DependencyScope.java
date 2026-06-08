package com.zolt.resolve;

public enum DependencyScope {
    COMPILE(true, true, false, false, false, true, "compile"),
    RUNTIME(false, true, false, false, false, true, "runtime"),
    DEV(false, true, false, false, false, false, "dev"),
    TEST(false, false, true, false, false, false, "test"),
    PROVIDED(true, false, false, false, false, false, "provided"),
    PROCESSOR(false, false, false, true, false, false, "processor"),
    TEST_PROCESSOR(false, false, false, false, true, false, "test-processor"),
    QUARKUS_DEPLOYMENT(false, false, false, false, false, false, "quarkus-deployment");

    private final boolean mainCompileClasspath;
    private final boolean mainRuntimeClasspath;
    private final boolean testClasspath;
    private final boolean mainProcessorClasspath;
    private final boolean testProcessorClasspath;
    private final boolean packagedByDefault;
    private final String lockfileName;

    DependencyScope(
            boolean mainCompileClasspath,
            boolean mainRuntimeClasspath,
            boolean testClasspath,
            boolean mainProcessorClasspath,
            boolean testProcessorClasspath,
            boolean packagedByDefault,
            String lockfileName) {
        this.mainCompileClasspath = mainCompileClasspath;
        this.mainRuntimeClasspath = mainRuntimeClasspath;
        this.testClasspath = testClasspath;
        this.mainProcessorClasspath = mainProcessorClasspath;
        this.testProcessorClasspath = testProcessorClasspath;
        this.packagedByDefault = packagedByDefault;
        this.lockfileName = lockfileName;
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

    public boolean entersMainProcessorClasspath() {
        return mainProcessorClasspath;
    }

    public boolean entersTestProcessorClasspath() {
        return testProcessorClasspath;
    }

    public boolean packagedByDefault() {
        return packagedByDefault;
    }

    public String lockfileName() {
        return lockfileName;
    }
}
