package sh.zolt.sbom;

import java.util.List;
import java.util.Optional;

/**
 * One license and the dependencies that carry it. {@code label} is the SPDX id, the raw UNMAPPED
 * name, or {@code UNKNOWN}. {@code components} is sorted by coordinate.
 */
public record LicenseGroup(
        String label,
        SbomLicenseStatus status,
        Optional<String> url,
        List<LicenseComponentRef> components) {
    public LicenseGroup {
        url = url == null ? Optional.empty() : url;
        components = components == null ? List.of() : List.copyOf(components);
    }
}
