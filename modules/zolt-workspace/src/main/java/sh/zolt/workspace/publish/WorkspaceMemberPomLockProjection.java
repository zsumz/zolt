package sh.zolt.workspace.publish;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.lockfile.ZoltLockfile;
import sh.zolt.project.DependencyMetadata;
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
    public ZoltLockfile project(ProjectConfig memberConfig, ZoltLockfile aggregatedLock) {
        ExternalIndex externalIndex = new ExternalIndex();
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
        addExternal(projected, memberConfig.apiDependencies().keySet(), DependencyScope.COMPILE, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.managedApiDependencies(), DependencyScope.COMPILE, memberConfig,
                externalIndex);
        addWorkspace(projected, memberConfig.workspaceDependencies(), workspaceByCoordinate);
        addExternal(projected, memberConfig.dependencies().keySet(), DependencyScope.COMPILE, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.managedDependencies(), DependencyScope.COMPILE, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.runtimeDependencies().keySet(), DependencyScope.RUNTIME, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.managedRuntimeDependencies(), DependencyScope.RUNTIME, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.providedDependencies().keySet(), DependencyScope.PROVIDED, memberConfig,
                externalIndex);
        addExternal(projected, memberConfig.managedProvidedDependencies(), DependencyScope.PROVIDED, memberConfig,
                externalIndex);
        return new ZoltLockfile(1, List.copyOf(projected.values()), List.of());
    }

    private static void addWorkspace(
            Map<String, LockPackage> projected,
            Map<String, String> workspaceDependencies,
            Map<String, LockPackage> workspaceByCoordinate) {
        for (Map.Entry<String, String> entry : workspaceDependencies.entrySet()) {
            String coordinate = entry.getKey();
            if (projected.containsKey(coordinate)) {
                continue;
            }
            LockPackage provider = workspaceByCoordinate.get(coordinate);
            if (provider == null) {
                continue;
            }
            projected.put(coordinate, new LockPackage(
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
            ProjectConfig memberConfig,
            ExternalIndex externalIndex) {
        for (String coordinate : coordinates) {
            if (projected.containsKey(coordinate)) {
                continue;
            }
            // The member declares a GA coordinate; the variant it actually depends on comes from its
            // dependency metadata (a classifier/type). Resolving the aggregated-lock entry for THAT variant
            // is what stops a member from taking a sibling variant's version — the netty case where the
            // osx-classified dep would otherwise inherit the linux variant's version.
            LockArtifactVariant variant = declaredVariant(memberConfig, coordinate, scope);
            LockPackage resolved = externalIndex.resolve(coordinate, variant);
            String version = resolved != null ? resolved.version() : declaredVersion(memberConfig, coordinate);
            if (version == null) {
                continue;
            }
            String source = resolved != null ? resolved.source() : ProjectConfig.MAVEN_CENTRAL;
            projected.put(coordinate, new LockPackage(
                    packageId(coordinate),
                    version,
                    source,
                    scope,
                    true,
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    Optional.empty(),
                    List.of()));
        }
    }

    /**
     * The variant a member depends on for a declared GA coordinate, from its dependency metadata. The
     * classifier maps directly; the {@code <type>} is the artifact extension (defaulting to {@code jar}).
     * The metadata key mirrors {@code PublishPomGenerator}'s so the version the projection resolves and
     * the classifier/type the generator renders describe the same artifact.
     */
    private static LockArtifactVariant declaredVariant(
            ProjectConfig memberConfig, String coordinate, DependencyScope scope) {
        DependencyMetadata metadata = memberConfig.dependencyMetadata().get(metadataKey(scope, coordinate));
        if (metadata == null) {
            return new LockArtifactVariant("jar", Optional.empty());
        }
        String extension = metadata.type() == null ? "jar" : metadata.type();
        return new LockArtifactVariant(extension, Optional.ofNullable(metadata.classifier()));
    }

    private static String metadataKey(DependencyScope scope, String coordinate) {
        return switch (scope) {
            case RUNTIME -> DependencyMetadata.key("runtime.dependencies", coordinate);
            case PROVIDED -> DependencyMetadata.key("provided.dependencies", coordinate);
            default -> DependencyMetadata.key("dependencies", coordinate);
        };
    }

    /** Aggregated-lock externals indexed both by GA and by (GA, variant) for variant-exact resolution. */
    private static final class ExternalIndex {
        private final Map<String, LockPackage> byCoordinate = new LinkedHashMap<>();
        private final Map<String, LockPackage> byVariant = new LinkedHashMap<>();

        void add(String coordinate, LockPackage lockPackage) {
            byCoordinate.putIfAbsent(coordinate, lockPackage);
            byVariant.putIfAbsent(variantKey(coordinate, LockArtifactVariant.of(lockPackage)), lockPackage);
        }

        LockPackage resolve(String coordinate, LockArtifactVariant variant) {
            LockPackage exact = byVariant.get(variantKey(coordinate, variant));
            return exact != null ? exact : byCoordinate.get(coordinate);
        }

        private static String variantKey(String coordinate, LockArtifactVariant variant) {
            return coordinate + "#" + variant.key();
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
