package sh.zolt.lockfile;

import sh.zolt.dependency.PackageId;
import java.util.Optional;

/**
 * A resolved dependency-graph edge target: the {@link PackageId}, version, and {@link LockArtifactVariant}
 * the edge points at. This is the identity behind the strings stored in {@link LockPackage#dependencies()}.
 *
 * <p><strong>Wire format (back-compatible).</strong> A default variant (plain {@code jar}, no classifier)
 * encodes to exactly {@code groupId:artifactId:version} — the historical bare form — so a lock with no
 * variants stays byte-identical. A non-default variant appends its {@link LockArtifactVariant#key()} as a
 * fourth colon-delimited field: {@code groupId:artifactId:version:extension} or
 * {@code groupId:artifactId:version:extension|classifier}. Group, artifact, version, extension, and
 * classifier can none contain {@code :} (it is the Maven coordinate delimiter), so the field count alone
 * distinguishes a bare ref (3 fields) from a variant-qualified one (4). The variant token reuses the same
 * canonical {@code key()} used everywhere else — package sort tiebreak, aggregation lane key, and conflict
 * qualifier — so there is one variant spelling across the whole lock.
 */
public record LockDependencyEdge(PackageId packageId, String version, LockArtifactVariant variant) {
    public LockDependencyEdge {
        variant = variant == null ? new LockArtifactVariant("jar", Optional.empty()) : variant;
    }

    /** The variant-aware canonical edge string: bare {@code g:a:v} when default, else {@code g:a:v:key}. */
    public String encode() {
        String gav = gav();
        return variant.isDefault() ? gav : gav + ":" + variant.key();
    }

    /** The classifier-free {@code groupId:artifactId:version}, ignoring the variant. */
    public String gav() {
        return packageId.groupId() + ":" + packageId.artifactId() + ":" + version;
    }

    /** The variant-aware canonical edge string that points at {@code lockPackage}. */
    public static LockDependencyEdge of(LockPackage lockPackage) {
        return new LockDependencyEdge(
                lockPackage.packageId(), lockPackage.version(), LockArtifactVariant.of(lockPackage));
    }

    /** Encodes an edge target directly, without materializing the record. */
    public static String encode(PackageId packageId, String version, LockArtifactVariant variant) {
        return new LockDependencyEdge(packageId, version, variant).encode();
    }

    /**
     * Parses an edge string. A 3-field {@code g:a:v} is the default variant; a 4-field
     * {@code g:a:v:key} carries a non-default variant. Any other shape (a malformed or non-edge string)
     * yields {@link Optional#empty()} so callers can leave it untouched, matching the prior tolerant
     * behavior of the one edge-rewriting site.
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
        return Optional.empty();
    }
}
