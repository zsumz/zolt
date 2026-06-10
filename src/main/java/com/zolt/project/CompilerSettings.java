package com.zolt.project;

import java.util.List;

public record CompilerSettings(
        String generatedSources,
        String generatedTestSources,
        String release,
        String encoding,
        List<String> args,
        List<String> testArgs) {
    private static final String DEFAULT_GENERATED_SOURCES = "target/generated/sources/annotations";
    private static final String DEFAULT_GENERATED_TEST_SOURCES = "target/generated/test-sources/annotations";

    public CompilerSettings {
        generatedSources = stringOrDefault(generatedSources, DEFAULT_GENERATED_SOURCES);
        generatedTestSources = stringOrDefault(generatedTestSources, DEFAULT_GENERATED_TEST_SOURCES);
        release = stringOrEmpty(release);
        encoding = stringOrEmpty(encoding);
        args = copyArgs(args, "args");
        testArgs = copyArgs(testArgs, "testArgs");
    }

    public CompilerSettings(String generatedSources, String generatedTestSources) {
        this(generatedSources, generatedTestSources, "", "", List.of(), List.of());
    }

    public static CompilerSettings defaults() {
        return new CompilerSettings(
                DEFAULT_GENERATED_SOURCES,
                DEFAULT_GENERATED_TEST_SOURCES,
                "",
                "",
                List.of(),
                List.of());
    }

    private static String stringOrDefault(String value, String defaultValue) {
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private static String stringOrEmpty(String value) {
        return value == null || value.isBlank() ? "" : value;
    }

    private static List<String> copyArgs(List<String> values, String name) {
        if (values == null) {
            return List.of();
        }
        for (String value : values) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("Compiler " + name + " must contain non-empty strings.");
            }
        }
        return List.copyOf(values);
    }
}
