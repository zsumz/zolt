package sh.zolt.maven.metadata;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The result of discovering a coordinate's available versions across repositories.
 *
 * <p>{@code resolved} is false when no listing could be obtained from any repository (all missing,
 * or offline with no cache) — the caller reports status unknown. {@code versions} is the union
 * across repositories, deduped by exact version string and sorted ascending. {@code sourceByVersion}
 * attributes each version to the first repository (in query order) that listed it. {@code notes}
 * carries advisory staleness/offline/failure annotations in deterministic order.
 */
public record MetadataDiscovery(
        boolean resolved,
        List<String> versions,
        Map<String, String> sourceByVersion,
        List<String> notes) {
    public MetadataDiscovery {
        versions = versions == null ? List.of() : List.copyOf(versions);
        sourceByVersion = sourceByVersion == null ? Map.of() : Map.copyOf(sourceByVersion);
        notes = notes == null ? List.of() : List.copyOf(notes);
    }

    public Optional<String> source(String version) {
        return Optional.ofNullable(sourceByVersion.get(version));
    }
}
