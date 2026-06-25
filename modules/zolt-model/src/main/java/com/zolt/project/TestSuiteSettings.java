package com.zolt.project;

import java.util.List;

public record TestSuiteSettings(
        List<String> includeClassname,
        List<String> excludeClassname,
        List<String> includeTag,
        List<String> excludeTag) {
    public TestSuiteSettings {
        includeClassname = copyAndValidatePatterns("test.suites.includeClassname", includeClassname);
        excludeClassname = copyAndValidatePatterns("test.suites.excludeClassname", excludeClassname);
        includeTag = copyAndValidateTags("test.suites.includeTag", includeTag);
        excludeTag = copyAndValidateTags("test.suites.excludeTag", excludeTag);
    }

    public static TestSuiteSettings empty() {
        return new TestSuiteSettings(List.of(), List.of(), List.of(), List.of());
    }

    private static List<String> copyAndValidatePatterns(String label, List<String> values) {
        List<String> copied = List.copyOf(values == null ? List.of() : values);
        for (String value : copied) {
            validateNonBlank(label, value);
            if (containsControlOrWhitespace(value)) {
                throw new IllegalArgumentException(
                        "Invalid [" + label + "] value `" + value + "`. Patterns must not contain whitespace or control characters.");
            }
        }
        return copied;
    }

    private static List<String> copyAndValidateTags(String label, List<String> values) {
        List<String> copied = List.copyOf(values == null ? List.of() : values);
        for (String value : copied) {
            validateNonBlank(label, value);
            if (containsControlOrWhitespace(value) || value.indexOf(',') >= 0) {
                throw new IllegalArgumentException(
                        "Invalid [" + label + "] value `" + value + "`. Tags must not contain whitespace, commas, or control characters.");
            }
        }
        return copied;
    }

    private static void validateNonBlank(String label, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " requires a non-empty value.");
        }
    }

    private static boolean containsControlOrWhitespace(String value) {
        for (int index = 0; index < value.length(); index++) {
            char character = value.charAt(index);
            if (Character.isWhitespace(character) || Character.isISOControl(character)) {
                return true;
            }
        }
        return false;
    }
}
