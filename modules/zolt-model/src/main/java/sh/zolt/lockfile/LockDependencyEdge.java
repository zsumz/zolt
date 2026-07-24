package sh.zolt.lockfile;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.dependency.PackageId;
import java.util.Optional;

/**
 * A resolved dependency-graph edge target: the {@link PackageId}, version,
 * {@link LockArtifactVariant}, and resolved {@link DependencyScope} the edge points at. This is the
 * identity behind the strings stored in {@link LockPackage#dependencies()}.
 *
 * <p><strong>Wire format.</strong> Version-3 edges use five fields:
 * {@code groupId:artifactId:version:extension|classifier:scope}. The variant field is always present
 * (plain jars use {@code jar}) so the fifth scope field is unambiguous. The parser also accepts the
 * historical version-1 bare GAV and version-2 variant-qualified forms; those legacy edges carry no
 * scope and can resolve only when the candidate set is unambiguous.
 */
public record LockDependencyEdge(
        PackageId packageId,
        String version,
        LockArtifactVariant variant,
        Optional<DependencyScope> scope) {
    public LockDependencyEdge {
        variant = variant == null ? new LockArtifactVariant("jar", Optional.empty()) : variant;
        scope = scope == null ? Optional.empty() : scope;
    }

    /** Constructs a legacy scope-less edge, used only for parsing and compatibility tests. */
    public LockDependencyEdge(PackageId packageId, String version, LockArtifactVariant variant) {
        this(packageId, version, variant, Optional.empty());
    }

    /** Constructs a scope-qualified version-3 edge. */
    public LockDependencyEdge(
            PackageId packageId,
            String version,
            LockArtifactVariant variant,
            DependencyScope scope) {
        this(packageId, version, variant, Optional.of(scope));
    }

    /** The canonical edge string, scope-qualified for version 3 and historical when scope is absent. */
    public String encode() {
        String gav = gav();
        if (scope.isPresent()) {
            return gav + ":" + variant.key() + ":" + scope.orElseThrow().lockfileName();
        }
        return variant.isDefault() ? gav : gav + ":" + variant.key();
    }

    /** The classifier-free {@code groupId:artifactId:version}, ignoring the variant. */
    public String gav() {
        return packageId.groupId() + ":" + packageId.artifactId() + ":" + version;
    }

    /** The scope- and variant-qualified canonical edge string that points at {@code lockPackage}. */
    public static LockDependencyEdge of(LockPackage lockPackage) {
        return new LockDependencyEdge(
                lockPackage.packageId(),
                lockPackage.version(),
                LockArtifactVariant.of(lockPackage),
                lockPackage.scope());
    }

    /** Encodes a historical scope-less edge directly, retained for compatibility callers. */
    public static String encode(PackageId packageId, String version, LockArtifactVariant variant) {
        return new LockDependencyEdge(packageId, version, variant).encode();
    }

    /** Encodes a scope-qualified version-3 edge directly. */
    public static String encode(
            PackageId packageId,
            String version,
            LockArtifactVariant variant,
            DependencyScope scope) {
        return new LockDependencyEdge(packageId, version, variant, scope).encode();
    }

    /**
     * Parses a version-1, version-2, or version-3 edge string. Any malformed or non-edge string yields
     * {@link Optional#empty()} so tolerant edge-rewriting callers can leave it untouched.
     */
    public static Optional<LockDependencyEdge> parse(String edge) {
        String[] parts = edge.split(":", -1);
        if (parts.length == 3) {
            if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new LockDependencyEdge(
                    new PackageId(parts[0], parts[1]), parts[2], new LockArtifactVariant("jar", Optional.empty())));
        }
        if (parts.length == 4) {
            if (parts[0].isBlank() || parts[1].isBlank() || parts[2].isBlank() || parts[3].isBlank()) {
                return Optional.empty();
            }
            return Optional.of(new LockDependencyEdge(
                    new PackageId(parts[0], parts[1]), parts[2], LockArtifactVariant.fromKey(parts[3])));
        }
        if (parts.length == 5) {
            if (parts[0].isBlank()
                    || parts[1].isBlank()
                    || parts[2].isBlank()
                    || parts[3].isBlank()
                    || parts[4].isBlank()) {
                return Optional.empty();
            }
            Optional<DependencyScope> scope = scope(parts[4]);
            if (scope.isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(new LockDependencyEdge(
                    new PackageId(parts[0], parts[1]),
                    parts[2],
                    LockArtifactVariant.fromKey(parts[3]),
                    scope));
        }
        return Optional.empty();
    }

    private static Optional<DependencyScope> scope(String value) {
        for (DependencyScope scope : DependencyScope.values()) {
            if (scope.lockfileName().equals(value)) {
                return Optional.of(scope);
            }
        }
        return Optional.empty();
    }
}
