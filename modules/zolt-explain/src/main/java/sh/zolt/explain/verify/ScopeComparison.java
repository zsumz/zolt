package sh.zolt.explain.verify;

import java.util.List;

/**
 * The per-scope comparison for one module: artifacts that matched exactly, those that drifted in
 * version, and those present on only one side. All lists are deterministically ordered by the
 * comparator.
 */
public record ScopeComparison(
        VerifyScope scope,
        List<ResolvedArtifact> matched,
        List<VersionDrift> versionDrift,
        List<ResolvedArtifact> onlyInMaven,
        List<ResolvedArtifact> onlyInZolt) {

    public ScopeComparison {
        matched = matched == null ? List.of() : List.copyOf(matched);
        versionDrift = versionDrift == null ? List.of() : List.copyOf(versionDrift);
        onlyInMaven = onlyInMaven == null ? List.of() : List.copyOf(onlyInMaven);
        onlyInZolt = onlyInZolt == null ? List.of() : List.copyOf(onlyInZolt);
    }

    public boolean hasDifferences() {
        return !versionDrift.isEmpty() || !onlyInMaven.isEmpty() || !onlyInZolt.isEmpty();
    }

    public boolean isEmpty() {
        return matched.isEmpty() && !hasDifferences();
    }
}
