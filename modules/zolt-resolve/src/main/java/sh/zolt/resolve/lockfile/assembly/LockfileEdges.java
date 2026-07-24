package sh.zolt.resolve.lockfile.assembly;

import sh.zolt.lockfile.LockArtifactVariant;
import sh.zolt.lockfile.LockDependencyEdge;
import sh.zolt.resolve.graph.PackageNode;
import sh.zolt.resolve.graph.ResolutionEdge;
import sh.zolt.resolve.graph.ResolutionGraph;
import java.util.List;
import java.util.Optional;

/**
 * Emits the variant-qualified dependency-edge refs stored in a lock package's {@code dependencies}. The
 * target GAV comes from the resolved {@code to} node; the variant (classifier/extension) from the edge
 * request's {@code ArtifactDescriptor} — the resolve graph is where classifier identity is still known.
 * A request with no descriptor (the common transitive case) is the default jar, so the ref stays the bare
 * {@code groupId:artifactId:version} and a lock without variants is byte-identical to before.
 */
final class LockfileEdges {
    private LockfileEdges() {
    }

    static List<String> dependenciesFor(PackageNode node, ResolutionGraph graph) {
        return graph.edges().stream()
                .filter(edge -> edge.from().equals(node))
                .map(LockfileEdges::edgeRef)
                .distinct()
                .sorted()
                .toList();
    }

    private static String edgeRef(ResolutionEdge edge) {
        LockArtifactVariant variant = edge.request().artifactDescriptor()
                .map(descriptor -> new LockArtifactVariant(descriptor.extension(), descriptor.classifier()))
                .orElseGet(() -> new LockArtifactVariant("jar", Optional.empty()));
        return LockDependencyEdge.encode(edge.to().packageId(), edge.to().selectedVersion(), variant);
    }
}
