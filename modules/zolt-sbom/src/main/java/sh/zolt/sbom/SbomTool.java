package sh.zolt.sbom;

/**
 * A CycloneDX 1.5 {@code metadata.tools[]} entry (plain tools array form, universally consumed by
 * Dependency-Track/Grype/Trivy).
 */
public record SbomTool(String name, String version) {
    public SbomTool {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("SbomTool name must not be blank.");
        }
        version = version == null ? "" : version;
    }
}
