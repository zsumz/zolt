package sh.zolt.sbom;

import java.util.Optional;

/**
 * A single resolved license for a component. Dual licensing is represented as multiple discrete
 * {@code SbomLicense} objects (never an SPDX expression — Maven's dual-license semantics are
 * ambiguous).
 */
public record SbomLicense(
        SbomLicenseStatus status,
        Optional<String> spdxId,
        Optional<String> name,
        Optional<String> url) {
    private static final SbomLicense UNKNOWN =
            new SbomLicense(SbomLicenseStatus.UNKNOWN, Optional.empty(), Optional.empty(), Optional.empty());

    public SbomLicense {
        spdxId = spdxId == null ? Optional.empty() : spdxId;
        name = name == null ? Optional.empty() : name;
        url = url == null ? Optional.empty() : url;
    }

    public static SbomLicense spdx(String id) {
        return new SbomLicense(SbomLicenseStatus.SPDX, Optional.of(id), Optional.empty(), Optional.empty());
    }

    public static SbomLicense unmapped(Optional<String> name, Optional<String> url) {
        return new SbomLicense(SbomLicenseStatus.UNMAPPED, Optional.empty(), name, url);
    }

    public static SbomLicense unknown() {
        return UNKNOWN;
    }

    /** The label used to group this license in reports and to match it in the policy gate. */
    public String label() {
        return switch (status) {
            case SPDX -> spdxId.orElse("UNKNOWN");
            case UNMAPPED -> name.or(() -> url).orElse("(unspecified)");
            case UNKNOWN -> "UNKNOWN";
        };
    }

    /** The display name for a CycloneDX named-license object (UNMAPPED): the raw name, else the URL. */
    public String displayName() {
        return name.or(() -> url).orElse("UNMAPPED");
    }
}
