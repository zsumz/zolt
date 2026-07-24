package sh.zolt.workspace.publish;

import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Projects the aggregated workspace lock into an <em>SBOM-shaped</em> lockfile for one member, so
 * {@link WorkspaceMemberSbomGenerator} (running the single-project {@code LockSbomAssembler}) can emit
 * that member's COMPLETE CycloneDX graph — transitive components, artifact hashes, and dependency
 * edges intact.
 *
 * <p>Contrast {@link WorkspaceMemberPomLockProjection}, which is deliberately POM-shaped: it lists a
 * member's declared directs only, reconstructed with empty jar/pom hashes and empty edge lists. That
 * is correct for a POM (a POM declares only directs) but materially understates an SBOM, which is
 * supply-chain evidence: it must carry every reachable transitive component, each component's SHA-256,
 * and the external&#8594;external edges. The two projections coexist — the POM one feeds POM
 * generation, this one feeds SBOM generation.
 *
 * <p><strong>Closure.</strong> Starting from the member's direct coordinates (resolved against the
 * aggregated lock) and its workspace siblings, it walks {@link LockPackage#dependencies()} edges
 * breadth-first and retains every reached package <em>as-is</em> — preserving artifact paths,
 * jar/pom/artifact SHA-256 hashes, source repositories, scopes, policies, and the edge lists. Nothing
 * is reconstructed; scope filtering (dev/test/provided excluded by default) is left to the assembler's
 * {@code SbomScopeSelection}, which reads the carried-through scopes. Workspace siblings carry no edges
 * in the aggregated lock, so a sibling contributes itself (a first-party {@code source="workspace"}
 * library component) and the {@code root -> sibling} edge; a member's external transitive closure comes
 * from its own direct externals' edges — mirroring the member's published POM, whose sibling
 * dependencies resolve transitively through the sibling's own POM.
 *
 * <p><strong>Directness (fact 6).</strong> The aggregated lock's {@code direct} flag is OR'd across
 * every member and must NOT drive a member's SBOM. {@code LockSbomAssembler} turns each
 * {@code direct()} package into a {@code root -> package} edge, so every closure package's flag is
 * re-stamped to this member's view: {@code true} for a coordinate the member itself declares direct,
 * {@code false} for a transitive.
 */
public final class WorkspaceMemberSbomLockProjection {
    /**
     * @param memberConfig the member's (policy-merged) effective config — the sole source of directness
     * @param aggregatedLock the workspace root lock — the source of resolved versions, hashes, and edges
     * @return an SBOM-shaped lockfile: the member's full reachable closure, carried through as-is
     */
    public ZoltLockfile project(ProjectConfig memberConfig, ZoltLockfile aggregatedLock) {
        Map<String, LockPackage> byGav = new LinkedHashMap<>();
        Map<String, LockPackage> externalByCoordinate = new LinkedHashMap<>();
        Map<String, LockPackage> workspaceByCoordinate = new LinkedHashMap<>();
        for (LockPackage lockPackage : aggregatedLock.packages()) {
            byGav.putIfAbsent(gav(lockPackage), lockPackage);
            String coordinate = coordinate(lockPackage);
            if (lockPackage.workspace().isPresent()) {
                workspaceByCoordinate.putIfAbsent(coordinate, lockPackage);
            } else {
                externalByCoordinate.putIfAbsent(coordinate, lockPackage);
            }
        }

        // The member's authoritative direct set (by resolved g:a:v) and the closure's BFS roots, ordered
        // exactly like the POM projection: api, workspace-api, compile, workspace, runtime, provided.
        Set<String> directGav = new LinkedHashSet<>();
        Deque<LockPackage> roots = new ArrayDeque<>();
        addRoots(roots, directGav, memberConfig.workspaceApiDependencies().keySet(), workspaceByCoordinate);
        addRoots(roots, directGav, memberConfig.apiDependencies().keySet(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.managedApiDependencies(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.workspaceDependencies().keySet(), workspaceByCoordinate);
        addRoots(roots, directGav, memberConfig.dependencies().keySet(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.managedDependencies(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.runtimeDependencies().keySet(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.managedRuntimeDependencies(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.providedDependencies().keySet(), externalByCoordinate);
        addRoots(roots, directGav, memberConfig.managedProvidedDependencies(), externalByCoordinate);

        // Breadth-first over the aggregated lock's dependency edges (bare g:a:v), retaining each reached
        // package as-is. Insertion-ordered so the projected lock is deterministic.
        Map<String, LockPackage> reached = new LinkedHashMap<>();
        Deque<LockPackage> queue = new ArrayDeque<>(roots);
        while (!queue.isEmpty()) {
            LockPackage current = queue.removeFirst();
            String gav = gav(current);
            if (reached.containsKey(gav)) {
                continue;
            }
            reached.put(gav, current);
            for (String edge : current.dependencies()) {
                LockPackage target = byGav.get(edge);
                if (target != null && !reached.containsKey(edge)) {
                    queue.addLast(target);
                }
            }
        }

        List<LockPackage> projected = new ArrayList<>(reached.size());
        for (LockPackage lockPackage : reached.values()) {
            projected.add(withDirect(lockPackage, directGav.contains(gav(lockPackage))));
        }
        return new ZoltLockfile(1, List.copyOf(projected), List.of());
    }

    private static void addRoots(
            Deque<LockPackage> roots,
            Set<String> directGav,
            Set<String> coordinates,
            Map<String, LockPackage> byCoordinate) {
        for (String coordinate : coordinates) {
            LockPackage resolved = byCoordinate.get(coordinate);
            if (resolved == null) {
                continue;
            }
            if (directGav.add(gav(resolved))) {
                roots.addLast(resolved);
            }
        }
    }

    /** Carries a package through unchanged, re-stamping only the {@code direct} flag to the member view. */
    private static LockPackage withDirect(LockPackage lockPackage, boolean direct) {
        if (lockPackage.direct() == direct) {
            return lockPackage;
        }
        return new LockPackage(
                lockPackage.packageId(),
                lockPackage.version(),
                lockPackage.source(),
                lockPackage.scope(),
                direct,
                lockPackage.jar(),
                lockPackage.pom(),
                lockPackage.jarSha256(),
                lockPackage.pomSha256(),
                lockPackage.artifact(),
                lockPackage.artifactType(),
                lockPackage.artifactSha256(),
                lockPackage.workspace(),
                lockPackage.workspaceOutput(),
                lockPackage.dependencies(),
                lockPackage.members(),
                lockPackage.exportedBy(),
                lockPackage.policies(),
                lockPackage.toolGroups());
    }

    private static String gav(LockPackage lockPackage) {
        return lockPackage.packageId() + ":" + lockPackage.version();
    }

    private static String coordinate(LockPackage lockPackage) {
        return lockPackage.packageId().groupId() + ":" + lockPackage.packageId().artifactId();
    }
}
