package sh.zolt.sbom;

import java.util.List;
import java.util.Map;

/**
 * Resolved dependency licenses keyed by {@code "g:a:v"} coordinate, plus the sorted list of
 * coordinates that resolved to {@code UNKNOWN} (used to emit a single aggregated warning rather than
 * interleaving per-package warnings with the SBOM output).
 */
public record LicenseIndex(Map<String, List<SbomLicense>> byCoordinate, List<String> unresolved) {
    public LicenseIndex {
        byCoordinate = byCoordinate == null ? Map.of() : Map.copyOf(byCoordinate);
        unresolved = unresolved == null ? List.of() : List.copyOf(unresolved);
    }

    public static LicenseIndex empty() {
        return new LicenseIndex(Map.of(), List.of());
    }

    /** Licenses for a coordinate, or an empty list when licenses were not resolved for it. */
    public List<SbomLicense> forCoordinate(String coordinate) {
        return byCoordinate.getOrDefault(coordinate, List.of());
    }
}
