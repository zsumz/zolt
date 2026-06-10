package com.zolt.project;

import java.util.List;

public record DependencyMetadata(
        String section,
        String coordinate,
        String version,
        boolean managed,
        String workspace,
        boolean optional,
        boolean publishOnly,
        List<DependencyExclusionSpec> exclusions) {
    public DependencyMetadata {
        section = normalize(section);
        coordinate = normalize(coordinate);
        version = version == null || version.isBlank() ? null : version;
        workspace = workspace == null || workspace.isBlank() ? null : workspace;
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    public static String key(String section, String coordinate) {
        return section + "|" + coordinate;
    }

    public boolean emptyMetadata() {
        return !optional && !publishOnly && exclusions.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dependency metadata section and coordinate are required.");
        }
        return value;
    }
}
