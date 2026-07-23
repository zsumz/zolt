package sh.zolt.sbom;

import java.util.List;

/**
 * A CycloneDX {@code component}. Used both for the {@code metadata.component} (the root project or
 * workspace) and for every dependency entry in {@code components[]}.
 *
 * <p>{@code hashes} are kept in a fixed, caller-sorted order. Licenses are attached in stage 2.
 */
public record SbomComponent(
        SbomComponentType type,
        String bomRef,
        String group,
        String name,
        String version,
        String purl,
        SbomComponentScope scope,
        List<SbomHash> hashes) {
    public SbomComponent {
        hashes = hashes == null ? List.of() : List.copyOf(hashes);
    }
}
