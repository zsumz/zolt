package sh.zolt.workspace.publish;

import sh.zolt.dependency.DependencyScope;
import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.lockfile.LockDependencyEdge;
import sh.zolt.lockfile.LockPackage;
import sh.zolt.project.DependencyMetadata;
import sh.zolt.project.ProjectConfig;
import sh.zolt.publish.PublishDependencyMetadataKey;
import sh.zolt.publish.PublishException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Variant-identity helpers shared by the per-member POM and SBOM projections: the variant a member
 * <em>declares</em> for a GA coordinate, and an index of aggregated-lock externals resolvable by that
 * variant. Both projections resolve a member's declared coordinate to the aggregated entry for the
 * member's OWN variant, so a member never inherits a sibling variant's version (the netty case).
 */
final class MemberDependencyVariants {
    private MemberDependencyVariants() {
    }

    /** The variant-qualified edge ref that points at (and uniquely keys) this package. */
    static String ref(LockPackage lockPackage) {
        return LockDependencyEdge.of(lockPackage).encode();
    }

    /**
     * The variant a member depends on for a declared GA coordinate, from its dependency metadata — the
     * classifier maps directly and {@code <type>} is the artifact extension (default {@code jar}). The
     * metadata key mirrors {@code PublishPomGenerator}'s so the version a projection resolves and the
     * classifier/type the POM renders describe the same artifact.
     */
    static LockArtifactVariant declaredVariant(ProjectConfig config, String coordinate, DependencyScope scope) {
        DependencyMetadata metadata =
                config.dependencyMetadata().get(PublishDependencyMetadataKey.of(config, scope, coordinate));
        if (metadata == null) {
            return new LockArtifactVariant("jar", Optional.empty());
        }
        String extension = metadata.type() == null ? "jar" : metadata.type();
        return new LockArtifactVariant(extension, Optional.ofNullable(metadata.classifier()));
    }

    /** Aggregated-lock externals indexed both by GA and by (GA, variant) for variant-exact resolution. */
    static final class ExternalIndex {
        private final Map<String, LockPackage> byCoordinate = new LinkedHashMap<>();
        private final Map<String, LockPackage> byVariant = new LinkedHashMap<>();

        void add(String coordinate, LockPackage lockPackage) {
            byCoordinate.putIfAbsent(coordinate, lockPackage);
            byVariant.putIfAbsent(variantKey(coordinate, LockArtifactVariant.of(lockPackage)), lockPackage);
        }

        LockPackage resolve(String coordinate, LockArtifactVariant variant) {
            LockPackage exact = byVariant.get(variantKey(coordinate, variant));
            if (exact != null) {
                return exact;
            }
            if (!byCoordinate.containsKey(coordinate)) {
                return null;
            }
            throw new PublishException(
                    "Workspace zolt.lock does not contain the declared artifact variant `"
                            + coordinate
                            + ":"
                            + variant.key()
                            + "`. Run `zolt resolve --workspace` to regenerate the lock before publishing.");
        }

        private static String variantKey(String coordinate, LockArtifactVariant variant) {
            return coordinate + "#" + variant.key();
        }
    }
}
