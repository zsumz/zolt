package com.zolt.project;

import java.util.List;

public record GeneratedSourceStep(
        String id,
        GeneratedSourceKind kind,
        String language,
        String output,
        List<String> inputs,
        boolean required,
        boolean clean) {
    public GeneratedSourceStep {
        id = requireNonBlank(id, "Generated source step id");
        kind = kind == null ? GeneratedSourceKind.DECLARED_ROOT : kind;
        language = requireNonBlank(language, "Generated source language");
        output = requireNonBlank(output, "Generated source output");
        inputs = inputs == null ? List.of() : List.copyOf(inputs);
        for (String input : inputs) {
            requireNonBlank(input, "Generated source input");
        }
    }

    private static String requireNonBlank(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be a non-empty string.");
        }
        return value;
    }
}
