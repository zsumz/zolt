package com.zolt.build;

import java.util.List;

public record TestJvmArguments(List<String> values) {
    private static final TestJvmArguments EMPTY = new TestJvmArguments(List.of());

    public TestJvmArguments {
        if (values == null) {
            values = List.of();
        }
        for (String value : values) {
            validate(value);
        }
        values = List.copyOf(values);
    }

    public static TestJvmArguments empty() {
        return EMPTY;
    }

    public static TestJvmArguments fromCli(List<String> values) {
        return new TestJvmArguments(values);
    }

    private static void validate(String value) {
        if (value == null || value.isBlank()) {
            throw new TestRunException("--jvm-arg requires a non-empty value.");
        }
        String argument = value.trim();
        String flag = argument.contains("=") ? argument.substring(0, argument.indexOf('=')) : argument;
        if (flag.equals("-cp")
                || flag.equals("-classpath")
                || flag.equals("--class-path")
                || flag.equals("-jar")
                || flag.equals("-m")
                || flag.equals("--module")
                || flag.equals("--module-path")
                || flag.equals("-p")) {
            throw new TestRunException(
                    "Invalid --jvm-arg `"
                            + argument
                            + "`. Zolt owns the test classpath, module path, and test runner main class.");
        }
    }
}
