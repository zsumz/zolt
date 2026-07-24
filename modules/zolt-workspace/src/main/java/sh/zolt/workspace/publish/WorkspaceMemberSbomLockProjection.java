package sh.zolt.workspace.publish;

import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;
import sh.zolt.workspace.resolve.WorkspaceMemberPolicyResolver;
import sh.zolt.workspace.service.Workspace;
import sh.zolt.workspace.service.WorkspaceMember;
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
 * {@code SbomScopeSelection}, which reads the carried-through scopes.
 *
 * <p><strong>Workspace siblings (fact: a sibling's own externals reach the consumer).</strong> The
 * aggregated lock attributes a sibling-owned external (guava, declared by acme-core) onto the consumer
 * (acme-http) classpath, yet a workspace lock entry deliberately carries an EMPTY
 * {@link LockPackage#dependencies()} — aggregation cannot attribute a sibling's own edges — so a plain
 * BFS would stop at the sibling and drop guava from acme-http's SBOM. This projection is therefore
 * workspace-aware: for every sibling reachable from the member it resolves the sibling's own
 * policy-merged {@link ProjectConfig} (via {@link WorkspaceMemberPolicyResolver}, exactly as
 * {@code WorkspacePublishService} does) and materializes a populated copy of the sibling's lock entry
 * whose {@code dependencies} list carries synthetic edges to the sibling's <em>propagating</em> direct
 * externals — its api/compile/runtime dependencies, the scopes that transitively land on a consumer's
 * classpath ({@code provided}/{@code dev}/{@code test} are NOT transitive and are excluded). Its own
 * workspace siblings recurse the same way, so a transitive sibling chain (http&#8594;core&#8594;util)
 * fully resolves. The unified BFS then walks those synthetic edges and each external's own edges,
 * pulling the sibling-owned transitive externals into the member's SBOM as-is.
 *
 * <p><strong>Directness (fact 6).</strong> The aggregated lock's {@code direct} flag is OR'd across
 * every member and must NOT drive a member's SBOM. {@code LockSbomAssembler} turns each
 * {@code direct()} package into a {@code root -> package} edge, so every closure package's flag is
 * re-stamped to this member's view: {@code true} only for a coordinate the member ITSELF declares
 * direct; sibling-owned externals and transitively-reached siblings are {@code false}.
 */
public final class WorkspaceMemberSbomLockProjection {
    /**
     * @param memberConfig the member's (policy-merged) effective config — the sole source of directness
     * @param aggregatedLock the workspace root lock — the source of resolved versions, hashes, and edges
     * @param workspace the enclosing workspace — supplies the sibling members whose configs are recursed
     * @param policyResolver merges each sibling's effective config, exactly as the publish flow resolves it
     * @return an SBOM-shaped lockfile: the member's full reachable closure, carried through as-is
     */
    public ZoltLockfile project(
            ProjectConfig memberConfig,
            ZoltLockfile aggregatedLock,
            Workspace workspace,
            WorkspaceMemberPolicyResolver policyResolver) {
        Map<String, LockPackage> byGav = new LinkedHashMap<>();
        Map<String, LockPackage> externalByCoordinate = new LinkedHashMap<>();
        Map<String, List<LockPackage>> externalCandidates = new LinkedHashMap<>();
        Map<String, LockPackage> workspaceByCoordinate = new LinkedHashMap<>();
        for (LockPackage lockPackage : aggregatedLock.packages()) {
            byGav.putIfAbsent(gav(lockPackage), lockPackage);
            String coordinate = coordinate(lockPackage);
            if (lockPackage.workspace().isPresent()) {
                workspaceByCoordinate.putIfAbsent(coordinate, lockPackage);
            } else {
                externalByCoordinate.putIfAbsent(coordinate, lockPackage);
                externalCandidates.computeIfAbsent(coordinate, key -> new ArrayList<>()).add(lockPackage);
            }
        }

        // Resolve the member's transitive workspace-sibling closure and synthesize each sibling's edges.
        // A workspace lock entry carries no edges, so this materializes a populated copy per sibling whose
        // dependencies point at its propagating direct externals (and its own workspace siblings). Both
        // root resolution and the BFS below then see those edges through the overlay.
        SiblingClosure closure = new SiblingClosure(
                workspace, policyResolver, workspaceByCoordinate, externalCandidates);
        Map<String, LockPackage> populatedSiblings = closure.populate(directWorkspaceCoordinates(memberConfig));
        for (Map.Entry<String, LockPackage> entry : populatedSiblings.entrySet()) {
            byGav.put(gav(entry.getValue()), entry.getValue());
            workspaceByCoordinate.put(entry.getKey(), entry.getValue());
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

    /** The member's own direct workspace-dependency coordinates: the seed for the sibling closure. */
    private static Set<String> directWorkspaceCoordinates(ProjectConfig config) {
        Set<String> coordinates = new LinkedHashSet<>();
        coordinates.addAll(config.workspaceApiDependencies().keySet());
        coordinates.addAll(config.workspaceDependencies().keySet());
        return coordinates;
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

    /** Carries a workspace-sibling package through unchanged except for a populated {@code dependencies} list. */
    private static LockPackage withDependencies(LockPackage lockPackage, List<String> dependencies) {
        if (lockPackage.dependencies().equals(dependencies)) {
            return lockPackage;
        }
        return new LockPackage(
                lockPackage.packageId(),
                lockPackage.version(),
                lockPackage.source(),
                lockPackage.scope(),
                lockPackage.direct(),
                lockPackage.jar(),
                lockPackage.pom(),
                lockPackage.jarSha256(),
                lockPackage.pomSha256(),
                lockPackage.artifact(),
                lockPackage.artifactType(),
                lockPackage.artifactSha256(),
                lockPackage.workspace(),
                lockPackage.workspaceOutput(),
                dependencies,
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

    private static String memberCoordinate(ProjectConfig config) {
        return config.project().group() + ":" + config.project().name();
    }

    /**
     * Resolves the member's transitive workspace-sibling closure, materializing per-sibling lock copies
     * whose {@code dependencies} carry synthetic edges the aggregated lock cannot. Each sibling contributes
     * edges to its api/compile/runtime externals (the scopes that transitively reach a consumer's
     * classpath) and to its own workspace siblings, which are recursed the same way.
     */
    private static final class SiblingClosure {
        private final Workspace workspace;
        private final WorkspaceMemberPolicyResolver policyResolver;
        private final Map<String, LockPackage> workspaceByCoordinate;
        private final Map<String, List<LockPackage>> externalCandidates;
        private final Map<String, WorkspaceMember> membersByCoordinate = new LinkedHashMap<>();
        private final Map<String, ProjectConfig> effectiveConfigs = new LinkedHashMap<>();
        private final Map<String, LockPackage> populated = new LinkedHashMap<>();
        private final Set<String> visited = new LinkedHashSet<>();

        SiblingClosure(
                Workspace workspace,
                WorkspaceMemberPolicyResolver policyResolver,
                Map<String, LockPackage> workspaceByCoordinate,
                Map<String, List<LockPackage>> externalCandidates) {
            this.workspace = workspace;
            this.policyResolver = policyResolver;
            this.workspaceByCoordinate = workspaceByCoordinate;
            this.externalCandidates = externalCandidates;
            for (WorkspaceMember member : workspace.members()) {
                membersByCoordinate.putIfAbsent(memberCoordinate(member.config()), member);
            }
        }

        /**
         * Populates every sibling reachable from {@code seedCoordinates}, returning a coordinate&#8594;copy
         * map whose values carry the synthesized dependency edges. Siblings with no propagating externals
         * (and no workspace siblings) come back byte-identical to their aggregated entry.
         */
        Map<String, LockPackage> populate(Set<String> seedCoordinates) {
            Deque<String> queue = new ArrayDeque<>(seedCoordinates);
            while (!queue.isEmpty()) {
                String coordinate = queue.removeFirst();
                if (!visited.add(coordinate)) {
                    continue;
                }
                LockPackage siblingPackage = workspaceByCoordinate.get(coordinate);
                WorkspaceMember member = membersByCoordinate.get(coordinate);
                if (siblingPackage == null || member == null) {
                    // Sibling absent from the lock, or not a known member: it is still carried as a bare
                    // first-party component by the BFS, matching the prior sibling-only behavior.
                    continue;
                }
                ProjectConfig config = effectiveConfig(member);
                List<String> edges = new ArrayList<>();
                Set<String> seen = new LinkedHashSet<>();
                // Propagating externals only: api + compile + runtime. provided/dev/test are NOT
                // transitive, so a sibling's provided/test externals never reach the consumer's SBOM.
                addExternalEdges(edges, seen, member.path(), config.apiDependencies().keySet());
                addExternalEdges(edges, seen, member.path(), config.managedApiDependencies());
                addExternalEdges(edges, seen, member.path(), config.dependencies().keySet());
                addExternalEdges(edges, seen, member.path(), config.managedDependencies());
                addExternalEdges(edges, seen, member.path(), config.runtimeDependencies().keySet());
                addExternalEdges(edges, seen, member.path(), config.managedRuntimeDependencies());
                // Siblings-of-siblings: edge to the workspace sibling, and recurse into it the same way.
                addWorkspaceEdges(edges, seen, queue, config.workspaceApiDependencies().keySet());
                addWorkspaceEdges(edges, seen, queue, config.workspaceDependencies().keySet());
                populated.put(coordinate, withDependencies(siblingPackage, List.copyOf(edges)));
            }
            return populated;
        }

        private void addExternalEdges(
                List<String> edges, Set<String> seen, String memberPath, Set<String> coordinates) {
            for (String coordinate : coordinates) {
                LockPackage resolved = resolveExternal(coordinate, memberPath);
                if (resolved != null && seen.add(gav(resolved))) {
                    edges.add(gav(resolved));
                }
            }
        }

        private void addWorkspaceEdges(
                List<String> edges, Set<String> seen, Deque<String> queue, Set<String> coordinates) {
            for (String coordinate : coordinates) {
                LockPackage resolved = workspaceByCoordinate.get(coordinate);
                if (resolved != null && seen.add(gav(resolved))) {
                    edges.add(gav(resolved));
                }
                queue.addLast(coordinate);
            }
        }

        /**
         * Member-attribution guard: when variant identity yields multiple lock entries sharing a GA, prefer
         * the entry attributed to this sibling — the one whose {@code members} list carries the sibling's
         * workspace path. With a single entry (today) this is exactly first-wins, so it is forward-compatible
         * with variant identity without depending on it.
         */
        private LockPackage resolveExternal(String coordinate, String memberPath) {
            List<LockPackage> candidates = externalCandidates.get(coordinate);
            if (candidates == null || candidates.isEmpty()) {
                return null;
            }
            if (candidates.size() == 1) {
                return candidates.get(0);
            }
            for (LockPackage candidate : candidates) {
                if (candidate.members().contains(memberPath)) {
                    return candidate;
                }
            }
            return candidates.get(0);
        }

        private ProjectConfig effectiveConfig(WorkspaceMember member) {
            return effectiveConfigs.computeIfAbsent(
                    member.path(), key -> policyResolver.merge(workspace, member));
        }
    }
}
