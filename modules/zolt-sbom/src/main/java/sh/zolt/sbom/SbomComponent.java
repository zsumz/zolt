package sh.zolt.sbom;

import java.util.List;

/**
 * A CycloneDX {@code component}. Used both for the {@code metadata.component} (the root project or
 * workspace) and for every dependency entry in {@code components[]}.
 *
 * <p>{@code hashes} are kept in a fixed, caller-sorted order. {@code licenses} carries the resolved
 * SPDX/UNMAPPED licenses (UNKNOWN entries are dropped before emission so the SBOM omits the field).
 */
public record SbomComponent(
        SbomComponentType type,
        String bomRef,
        String group,
        String name,
        String version,
        String purl,
        SbomComponentScope scope,
        List<SbomHash> hashes,
        List<SbomLicense> licenses) {
    public SbomComponent {
        hashes = hashes == null ? List.of() : List.copyOf(hashes);
        licenses = licenses == null ? List.of() : List.copyOf(licenses);
    }
}
