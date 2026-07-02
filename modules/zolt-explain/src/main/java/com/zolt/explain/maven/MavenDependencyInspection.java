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
        boolean scopeDeclared,
        String classifier,
        List<MavenDependencyExclusion> exclusions) {
    public MavenDependencyInspection(
            String scope,
            String coordinate,
            String version,
            String type,
            boolean optional,
            boolean managed,
            boolean importedBom,
            boolean scopeDeclared,
            List<MavenDependencyExclusion> exclusions) {
        this(scope, coordinate, version, type, optional, managed, importedBom, scopeDeclared, "", exclusions);
    }

    public MavenDependencyInspection(
            String scope,
            String coordinate,
            String version,
            String type,
            boolean optional,
            boolean managed,
            boolean importedBom,
            List<MavenDependencyExclusion> exclusions) {
        this(scope, coordinate, version, type, optional, managed, importedBom, true, exclusions);
    }

    public MavenDependencyInspection {
        classifier = classifier == null ? "" : classifier;
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }
}
