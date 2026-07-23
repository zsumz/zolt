package sh.zolt.sbom;

/**
 * Classification of a resolved dependency license.
 *
 * <ul>
 *   <li>{@link #SPDX} — normalized to a curated SPDX identifier; emitted as {@code {"license":{"id"}}}.
 *   <li>{@link #UNMAPPED} — a license was declared but does not match the curated mapping; the raw
 *       name and URL are kept verbatim (never guessed into a nearby id) and emitted as
 *       {@code {"license":{"name", "url"}}}.
 *   <li>{@link #UNKNOWN} — no license could be read from the cached POM chain; omitted from SBOM
 *       component licenses but surfaced in reports and the policy gate.
 * </ul>
 */
public enum SbomLicenseStatus {
    SPDX("spdx"),
    UNMAPPED("unmapped"),
    UNKNOWN("unknown");

    private final String jsonValue;

    SbomLicenseStatus(String jsonValue) {
        this.jsonValue = jsonValue;
    }

    public String jsonValue() {
        return jsonValue;
    }
}
