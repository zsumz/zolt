package sh.zolt.sbom;

/** A dependency attributed to a license group in the {@code zolt licenses} report. */
public record LicenseComponentRef(String coordinate, String purl) {
}
