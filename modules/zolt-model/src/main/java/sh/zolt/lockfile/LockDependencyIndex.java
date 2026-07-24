package sh.zolt.lockfile;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves {@link LockPackage#dependencies()} edge strings back to the {@link LockPackage} they point at,
 * carrying variant identity end to end. Every edge consumer (tree/why lookups, the SBOM dependency graph,
 * the workspace projections' BFS) builds one of these over its candidate packages and calls
 * {@link #resolve(String)} instead of a bare {@code groupId:artifactId:version} map lookup.
 *
 * <p><strong>Resolution.</strong> A variant-qualified edge ({@code g:a:v:key}) resolves to the exact
 * variant. A bare edge ({@code g:a:v}) resolves to the default (plain {@code jar}) variant when one is
 * present, otherwise to the sole variant at that {@code g:a:v} — so a lock written before variant
 * qualifiers (whose edges are all bare even to a classified artifact) still resolves each edge to the one
 * entry that ever existed at that coordinate. When a bare edge is genuinely ambiguous (several variants,
 * none default) it resolves to nothing rather than guess.
 */
public final class LockDependencyIndex {
    private final Map<String, LockPackage> byRef = new LinkedHashMap<>();
    private final Map<String, List<LockPackage>> byGav = new LinkedHashMap<>();

    public LockDependencyIndex(Iterable<LockPackage> packages) {
        for (LockPackage lockPackage : packages) {
            LockDependencyEdge edge = LockDependencyEdge.of(lockPackage);
            byRef.putIfAbsent(edge.encode(), lockPackage);
            byGav.computeIfAbsent(edge.gav(), key -> new ArrayList<>()).add(lockPackage);
        }
    }

    /** Resolves an edge string to the package it targets, honoring variant identity. */
    public Optional<LockPackage> resolve(String edge) {
        LockPackage exact = byRef.get(edge);
        if (exact != null) {
            return Optional.of(exact);
        }
        List<LockPackage> variants = byGav.get(edge);
        if (variants != null && variants.size() == 1) {
            return Optional.of(variants.get(0));
        }
        return Optional.empty();
    }
}
