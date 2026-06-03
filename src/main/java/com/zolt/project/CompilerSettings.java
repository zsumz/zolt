package com.zolt.project;

public record CompilerSettings(
        String generatedSources,
        String generatedTestSources) {
    private static final String DEFAULT_GENERATED_SOURCES = "target/generated/sources/annotations";
    private static final String DEFAULT_GENERATED_TEST_SOURCES = "target/generated/test-sources/annotations";

    public CompilerSettings {
        generatedSources = stringOrDefault(generatedSources, DEFAULT_GENERATED_SOURCES);
        generatedTestSources = stringOrDefault(generatedTestSources, DEFAULT_GENERATED_TEST_SOURCES);
    }

    public static CompilerSettings defaults() {
        return new CompilerSettings(
                DEFAULT_GENERATED_SOURCES,
                DEFAULT_GENERATED_TEST_SOURCES);
    }

    private static String stringOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
