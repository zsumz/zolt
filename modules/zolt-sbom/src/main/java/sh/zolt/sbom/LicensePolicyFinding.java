package sh.zolt.sbom;

/**
 * A non-permitted dependency under the license policy: which dependency, which license triggered it,
 * the verdict (WARN or VIOLATION), and the human-readable policy reason.
 */
public record LicensePolicyFinding(
        String coordinate,
        String purl,
        String license,
        LicenseVerdict verdict,
        String reason) {
}
