package sh.zolt.explain.gradle;

public record GradleDependencyInspection(
        String configuration,
        String notation,
        String resolvedCoordinate,
        String versionCatalogAlias,
        PlatformKind platformKind) {

    /**
     * Whether a dependency was declared through a Gradle {@code platform(...)} or
     * {@code enforcedPlatform(...)} wrapper. Platform imports are scope-agnostic in Zolt and route to
     * {@code [platforms]} (or, on a {@code java-platform} producer, to {@code [bom.imports]}) rather
     * than to a regular dependency section.
     */
    public enum PlatformKind {
        NONE,
        PLATFORM,
        ENFORCED_PLATFORM
    }

    public GradleDependencyInspection(
            String configuration,
            String notation,
            String resolvedCoordinate,
            String versionCatalogAlias) {
        this(configuration, notation, resolvedCoordinate, versionCatalogAlias, PlatformKind.NONE);
    }

    public boolean isPlatform() {
        return platformKind != PlatformKind.NONE;
    }
}
