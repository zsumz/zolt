package sh.zolt.sbom;

import java.util.List;
import java.util.Optional;

/**
 * The versioned, format-neutral SBOM subset Zolt assembles from a lockfile. A concrete writer
 * (currently {@link CycloneDxSbomWriter}) renders this into a wire format; a future SPDX writer would
 * consume the same model.
 *
 * <p>All collections are already sorted for deterministic emission: {@code components} by purl,
 * {@code dependencies} by ref, each {@code dependsOn} internally, hashes in fixed order. The
 * {@code timestamp} is a pre-formatted ISO-8601 instant, or empty when omitted (the default).
 */
public record SbomModel(
        String serialNumber,
        Optional<String> timestamp,
        List<SbomTool> tools,
        SbomComponent metadataComponent,
        List<SbomComponent> components,
        List<SbomDependency> dependencies) {
    public SbomModel {
        timestamp = timestamp == null ? Optional.empty() : timestamp;
        tools = tools == null ? List.of() : List.copyOf(tools);
        components = components == null ? List.of() : List.copyOf(components);
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
