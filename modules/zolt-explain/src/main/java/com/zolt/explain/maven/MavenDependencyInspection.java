package com.zolt.explain.maven;

import java.util.List;

public record MavenDependencyInspection(
        String scope,
        String coordinate,
        String version,
        String type,
        boolean optional,
        boolean managed,
        boolean importedBom,
        List<MavenDependencyExclusion> exclusions) {
    public MavenDependencyInspection {
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
}
