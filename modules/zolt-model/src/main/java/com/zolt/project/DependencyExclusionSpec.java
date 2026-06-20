package com.zolt.project;

public record DependencyExclusionSpec(
        String group,
        String artifact) {
    public DependencyExclusionSpec {
        group = normalize("group", group);
        artifact = normalize("artifact", artifact);
    }

    public String coordinate() {
        return group + ":" + artifact;
    }

    private static String normalize(String field, String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dependency exclusion " + field + " is required.");
        }
        return value;
    }
}
