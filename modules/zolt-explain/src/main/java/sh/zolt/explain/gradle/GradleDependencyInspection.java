package sh.zolt.explain.gradle;

public record GradleDependencyInspection(
        String configuration,
        String notation,
        String resolvedCoordinate,
        String versionCatalogAlias) {}
