package sh.zolt.workspace.publish;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.ProjectConfig;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Projects the aggregated workspace lock down to a single-project-shaped lockfile for one member, so
 * {@code PublishPomGenerator} can render that member's POM unchanged.
 *
 * <p><strong>Fact 6 (correctness).</strong> The aggregated lock's {@code direct} flag is OR'd across
 * every member and must NEVER drive a member's POM. Directness here comes exclusively from the
 * member's own zolt.toml (its declared api/compile/runtime/provided + workspace coordinates; dev and
 * test are excluded); only the resolved <em>versions</em> (and the inter-member provider GAVs) come
 * from the aggregated lock. Each config-declared direct coordinate becomes a direct
 * {@link LockPackage} at its aggregated-lock-resolved version.
 */
public final class WorkspaceMemberPomLockProjection {
    /**
     * @param memberConfig the member's (policy-merged) effective config — the sole source of directness
     * @param aggregatedLock the workspace root lock — the sole source of resolved versions and provider GAVs
     * @return a single-project lockfile whose direct packages are exactly the member's published directs
     */
    public ZoltLockfile project(
            String memberPath,
            ProjectConfig memberConfig,
            ZoltLockfile aggregatedLock) {
        MemberDependencyVariants.ExternalIndex externalIndex = new MemberDependencyVariants.ExternalIndex();
        Map<String, LockPackage> workspaceByCoordinate = new LinkedHashMap<>();
        for (LockPackage lockPackage : aggregatedLock.packages()) {
            String coordinate = coordinate(lockPackage.packageId());
            if (lockPackage.workspace().isPresent()) {
                workspaceByCoordinate.putIfAbsent(coordinate, lockPackage);
            } else {
                externalIndex.add(coordinate, lockPackage);
            }
        }

        Map<String, LockPackage> projected = new LinkedHashMap<>();
        addWorkspace(projected, memberConfig.workspaceApiDependencies(), workspaceByCoordinate);
        addExternal(projected, memberConfig.apiDependencies().keySet(), DependencyScope.COMPILE, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.managedApiDependencies(), DependencyScope.COMPILE, memberPath,
                memberConfig, externalIndex);
        addWorkspace(projected, memberConfig.workspaceDependencies(), workspaceByCoordinate);
        addExternal(projected, memberConfig.dependencies().keySet(), DependencyScope.COMPILE, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.managedDependencies(), DependencyScope.COMPILE, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.runtimeDependencies().keySet(), DependencyScope.RUNTIME, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.managedRuntimeDependencies(), DependencyScope.RUNTIME, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.providedDependencies().keySet(), DependencyScope.PROVIDED, memberPath,
                memberConfig, externalIndex);
        addExternal(projected, memberConfig.managedProvidedDependencies(), DependencyScope.PROVIDED, memberPath,
                memberConfig, externalIndex);
        return new ZoltLockfile(ZoltLockfile.CURRENT_VERSION, List.copyOf(projected.values()), List.of());
    }

    private static void addWorkspace(
            Map<String, LockPackage> projected,
            Map<String, String> workspaceDependencies,
            Map<String, LockPackage> workspaceByCoordinate) {
        for (Map.Entry<String, String> entry : workspaceDependencies.entrySet()) {
            String coordinate = entry.getKey();
            String key = coordinate + "#workspace";
            if (projected.containsKey(key)) {
                continue;
            }
            LockPackage provider = workspaceByCoordinate.get(coordinate);
            if (provider == null) {
                continue;
            }
            projected.put(key, new LockPackage(
                    provider.packageId(),
                    provider.version(),
                    "workspace",
                    DependencyScope.COMPILE,
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    provider.workspace().or(() -> Optional.of(entry.getValue())),
                    provider.workspaceOutput(),
                    List.of()));
        }
    }

    private static void addExternal(
            Map<String, LockPackage> projected,
            Set<String> coordinates,
            DependencyScope scope,
            String memberPath,
            ProjectConfig memberConfig,
            MemberDependencyVariants.ExternalIndex externalIndex) {
        for (String coordinate : coordinates) {
            sh.zolt.lockfile.LockArtifactVariant variant =
                    MemberDependencyVariants.declaredVariant(memberConfig, coordinate, scope);
            String key = coordinate + "#" + scope.lockfileName() + "#" + variant.key();
            if (projected.containsKey(key)) {
                continue;
            }
            // The member declares a GA coordinate; the variant it actually depends on comes from its
            // dependency metadata (a classifier/type). Resolving the aggregated-lock entry for THAT variant
            // is what stops a member from taking a sibling variant's version — the netty case where the
            // osx-classified dep would otherwise inherit the linux variant's version.
            LockPackage resolved = externalIndex.resolve(coordinate, variant, scope, memberPath);
            String version = resolved != null ? resolved.version() : declaredVersion(memberConfig, coordinate);
            if (version == null) {
                continue;
            }
            String source = resolved != null ? resolved.source() : ProjectConfig.MAVEN_CENTRAL;
            projected.put(key, new LockPackage(
                    packageId(coordinate),
                    version,
                    source,
                    scope,
                    true,
                    resolved == null ? Optional.empty() : resolved.jar(),
                    resolved == null ? Optional.empty() : resolved.pom(),
                    resolved == null ? Optional.empty() : resolved.jarSha256(),
                    resolved == null ? Optional.empty() : resolved.pomSha256(),
                    resolved == null ? Optional.empty() : resolved.artifact(),
                    resolved == null ? Optional.empty() : resolved.artifactType(),
                    resolved == null ? Optional.empty() : resolved.artifactSha256(),
                    List.of()));
        }
    }

    private static String declaredVersion(ProjectConfig memberConfig, String coordinate) {
        String version = memberConfig.apiDependencies().get(coordinate);
        if (version != null) {
            return version;
        }
        version = memberConfig.dependencies().get(coordinate);
        if (version != null) {
            return version;
        }
        version = memberConfig.runtimeDependencies().get(coordinate);
        if (version != null) {
            return version;
        }
        return memberConfig.providedDependencies().get(coordinate);
    }

    private static PackageId packageId(String coordinate) {
        String[] parts = coordinate.split(":", -1);
        return new PackageId(parts[0], parts.length > 1 ? parts[1] : "");
    }

    private static String coordinate(PackageId packageId) {
        return packageId.groupId() + ":" + packageId.artifactId();
    }
}
