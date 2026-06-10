package com.zolt.project;

import java.util.Optional;

public record DependencyPolicyExclusion(
        String group,
        String artifact,
        Optional<String> reason) {
    public DependencyPolicyExclusion {
        group = requireNonBlank(group, "Dependency policy exclusion group is required.");
        artifact = requireNonBlank(artifact, "Dependency policy exclusion artifact is required.");
        reason = reason == null ? Optional.empty() : reason.filter(value -> !value.isBlank());
    }

    public String coordinate() {
        return group + ":" + artifact;
    }

    private static String requireNonBlank(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
