package sh.zolt.explain.verify;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * A single reactor module (Maven) or workspace member (Zolt) with its resolved artifacts bucketed by
 * compared scope. Produced by {@link MavenDependencyTreeParser} for the Maven side and
 * {@link ZoltModuleMapper} for the Zolt side, then handed to {@link VerifyComparator}.
 *
 * @param groupId module group id
 * @param artifactId module artifact id (Zolt project name)
 * @param version the module's own version (reported, not compared)
 * @param packaging Maven packaging / Zolt {@code jar} (reported, not compared)
 * @param scopedArtifacts resolved artifacts per {@link VerifyScope}
 * @param unmappedScopes counts of resolved artifacts whose scope is outside the four compared scopes
 *     (e.g. Maven {@code system}, Zolt {@code dev}/{@code processor}), keyed by scope name — surfaced
 *     as notes so nothing is silently dropped
 */
public record ResolvedModule(
        String groupId,
        String artifactId,
        String version,
        String packaging,
        Map<VerifyScope, List<ResolvedArtifact>> scopedArtifacts,
        Map<String, Integer> unmappedScopes) {

    public ResolvedModule {
        groupId = groupId == null ? "" : groupId;
        artifactId = artifactId == null ? "" : artifactId;
        version = version == null ? "" : version;
        packaging = packaging == null || packaging.isBlank() ? "jar" : packaging;
        scopedArtifacts = normalizeScopes(scopedArtifacts);
        unmappedScopes = unmappedScopes == null ? Map.of() : new TreeMap<>(unmappedScopes);
    }

    /** Join key against the other side: {@code group:artifact}. */
    public String moduleKey() {
        return groupId + ":" + artifactId;
    }

    /** Resolved artifacts on the given scope, sorted, never null. */
    public List<ResolvedArtifact> artifacts(VerifyScope scope) {
        return scopedArtifacts.getOrDefault(scope, List.of());
    }

    private static Map<VerifyScope, List<ResolvedArtifact>> normalizeScopes(
            Map<VerifyScope, List<ResolvedArtifact>> input) {
        Map<VerifyScope, List<ResolvedArtifact>> normalized = new TreeMap<>();
        if (input != null) {
            for (Map.Entry<VerifyScope, List<ResolvedArtifact>> entry : input.entrySet()) {
                if (entry.getKey() == null || entry.getValue() == null || entry.getValue().isEmpty()) {
                    continue;
                }
                List<ResolvedArtifact> sorted = entry.getValue().stream()
                        .sorted(ResolvedArtifact.ORDER)
                        .toList();
                normalized.put(entry.getKey(), sorted);
            }
        }
        return normalized;
    }
}
