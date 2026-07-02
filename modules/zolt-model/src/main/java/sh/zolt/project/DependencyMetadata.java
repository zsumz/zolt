package sh.zolt.project;

import java.util.List;

public record DependencyMetadata(
        String section,
        String coordinate,
        String version,
        String versionRef,
        boolean managed,
        String workspace,
        boolean optional,
        boolean publishOnly,
        List<DependencyExclusionSpec> exclusions) {
    public DependencyMetadata {
        section = normalize(section);
        coordinate = normalize(coordinate);
        version = version == null || version.isBlank() ? null : version;
        versionRef = versionRef == null || versionRef.isBlank() ? null : versionRef;
        workspace = workspace == null || workspace.isBlank() ? null : workspace;
        exclusions = exclusions == null ? List.of() : List.copyOf(exclusions);
    }

    public DependencyMetadata(
            String section,
            String coordinate,
            String version,
            boolean managed,
            String workspace,
            boolean optional,
            boolean publishOnly,
            List<DependencyExclusionSpec> exclusions) {
        this(section, coordinate, version, null, managed, workspace, optional, publishOnly, exclusions);
    }

    public static String key(String section, String coordinate) {
        return section + "|" + coordinate;
    }

    public boolean emptyMetadata() {
        return versionRef == null && !optional && !publishOnly && exclusions.isEmpty();
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Dependency metadata section and coordinate are required.");
        }
        return value;
    }
}
