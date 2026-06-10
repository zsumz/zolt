package com.zolt.explain;

public record MavenDependencyInspection(
        String scope,
        String coordinate,
        String version,
        String type,
        boolean optional,
        boolean managed,
        boolean importedBom) {}
