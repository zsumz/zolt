package com.zolt.project;

public record BuildSettings(
        String source,
        String test,
        String output,
        String testOutput) {
    public static BuildSettings defaults() {
        return new BuildSettings(
                "src/main/java",
                "src/test/java",
                "target/classes",
                "target/test-classes");
    }
}
