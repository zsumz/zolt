package com.zolt.build;

import java.util.ArrayList;
import java.util.List;

public record TestSelection(
        List<String> classSelectors,
        List<MethodSelector> methodSelectors,
        List<String> classNamePatterns,
        List<String> includedTags,
        List<String> excludedTags) {
    private static final String JAVA_IDENTIFIER = "[A-Za-z_$][A-Za-z0-9_$]*";
    private static final String CLASS_NAME = JAVA_IDENTIFIER + "(\\." + JAVA_IDENTIFIER + ")*";
    private static final String METHOD_NAME = JAVA_IDENTIFIER;
    private static final TestSelection EMPTY = new TestSelection(List.of(), List.of(), List.of(), List.of(), List.of());

    public TestSelection {
        classSelectors = List.copyOf(classSelectors);
        methodSelectors = List.copyOf(methodSelectors);
        classNamePatterns = List.copyOf(classNamePatterns);
        includedTags = List.copyOf(includedTags);
        excludedTags = List.copyOf(excludedTags);
    }

    public static TestSelection empty() {
        return EMPTY;
    }

    public static TestSelection fromCli(
            List<String> testSelectors,
            List<String> classNamePatterns,
            List<String> includedTags,
            List<String> excludedTags) {
        List<String> classes = new ArrayList<>();
        List<MethodSelector> methods = new ArrayList<>();
        for (String selector : nullToEmpty(testSelectors)) {
            parseTestSelector(selector, classes, methods);
        }
        List<String> patterns = nullToEmpty(classNamePatterns).stream()
                .map(TestSelection::validatePattern)
                .toList();
        List<String> includes = nullToEmpty(includedTags).stream()
                .map(tag -> validateTag("--include-tag", tag))
                .toList();
        List<String> excludes = nullToEmpty(excludedTags).stream()
                .map(tag -> validateTag("--exclude-tag", tag))
                .toList();
        return new TestSelection(classes, methods, patterns, includes, excludes);
    }

    public boolean emptySelection() {
        return classSelectors.isEmpty()
                && methodSelectors.isEmpty()
                && classNamePatterns.isEmpty()
                && includedTags.isEmpty()
                && excludedTags.isEmpty();
    }

    public int explicitSelectorCount() {
        return classSelectors.size() + methodSelectors.size() + classNamePatterns.size();
    }

    public int tagSelectorCount() {
        return includedTags.size() + excludedTags.size();
    }

    private static void parseTestSelector(
            String selector,
            List<String> classes,
            List<MethodSelector> methods) {
        String value = requireValue("--test", selector);
        if (value.contains("*") || value.contains("?")) {
            throw new TestSelectionException(
                    "Invalid --test selector `"
                            + value
                            + "`. Use --tests for class-name patterns, or use --test com.example.ClassName[#methodName].");
        }
        int hash = value.indexOf('#');
        if (hash < 0) {
            classes.add(validateClassName("--test", value));
            return;
        }
        if (hash != value.lastIndexOf('#')) {
            throw new TestSelectionException(
                    "Invalid --test selector `" + value + "`. Method selectors must use one #.");
        }
        String className = value.substring(0, hash);
        String methodName = value.substring(hash + 1);
        methods.add(new MethodSelector(
                validateClassName("--test", className),
                validateMethodName(value, methodName)));
    }

    private static String validateClassName(String option, String value) {
        String className = requireValue(option, value);
        if (!className.matches(CLASS_NAME)) {
            throw new TestSelectionException(
                    "Invalid "
                            + option
                            + " class selector `"
                            + className
                            + "`. Use a fully qualified Java class name such as com.example.UserServiceTest.");
        }
        return className;
    }

    private static String validateMethodName(String selector, String value) {
        if (value == null || value.isBlank()) {
            throw new TestSelectionException(
                    "Invalid --test method selector `"
                            + selector
                            + "`. Use com.example.UserServiceTest#methodName.");
        }
        String methodName = value.trim();
        if (!methodName.matches(METHOD_NAME)) {
            throw new TestSelectionException(
                    "Invalid --test method selector `"
                            + selector
                            + "`. Use com.example.UserServiceTest#methodName.");
        }
        return methodName;
    }

    private static String validatePattern(String value) {
        String pattern = requireValue("--tests", value);
        if (containsControlOrWhitespace(pattern)) {
            throw new TestSelectionException(
                    "Invalid --tests pattern `" + pattern + "`. Patterns must not contain whitespace or control characters.");
        }
        if (pattern.indexOf('#') >= 0) {
            throw new TestSelectionException(
                    "Invalid --tests pattern `" + pattern + "`. Use --test com.example.ClassName#methodName for methods.");
        }
        return pattern;
    }

    private static String validateTag(String option, String value) {
        String tag = requireValue(option, value);
        if (containsControlOrWhitespace(tag) || tag.indexOf(',') >= 0) {
            throw new TestSelectionException(
                    "Invalid " + option + " value `" + tag + "`. Tags must not contain whitespace, commas, or control characters.");
        }
        return tag;
    }

    private static String requireValue(String option, String value) {
        if (value == null || value.isBlank()) {
            throw new TestSelectionException(option + " requires a non-empty value.");
        }
        return value.trim();
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

    private static List<String> nullToEmpty(List<String> values) {
        return values == null ? List.of() : values;
    }

    public record MethodSelector(String className, String methodName) {}
}
