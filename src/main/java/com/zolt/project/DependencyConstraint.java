package com.zolt.project;

import java.util.Optional;

public record DependencyConstraint(
        String coordinate,
        String version,
        DependencyConstraintKind kind,
        Optional<String> reason) {
    public DependencyConstraint {
        coordinate = requireNonBlank(coordinate, "Dependency constraint coordinate is required.");
        version = requireNonBlank(version, "Dependency constraint version is required.");
        kind = kind == null ? DependencyConstraintKind.STRICT : kind;
        reason = reason == null ? Optional.empty() : reason.filter(value -> !value.isBlank());
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
