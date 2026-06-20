package com.zolt.explain;

public record GradleDependencyInspection(
        String configuration,
        String notation,
        String resolvedCoordinate,
        String versionCatalogAlias) {}
