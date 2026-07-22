package sh.zolt.resolve;

import sh.zolt.dependency.PackageId;
import sh.zolt.maven.ArtifactDescriptor;
import sh.zolt.maven.Coordinate;
import sh.zolt.maven.repository.MavenRepositoryPathBuilder;
import sh.zolt.project.VersionPolicy;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import sh.zolt.resolve.materialization.RepositoryOverlayKind;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Decides whether a {@code -SNAPSHOT} dependency version may be resolved despite the
 * lockfile-determinism contract, which otherwise rejects external SNAPSHOTs.
 *
 * <p>SNAPSHOTs are permitted only for artifacts that already exist on this machine:
 * <ul>
 *   <li>workspace-member coordinates in the current workspace, and</li>
 *   <li>artifacts present in an enabled maven-local repository overlay.</li>
 * </ul>
 * Remote SNAPSHOT feeds remain unsupported by design, so nothing here ever consults the network.
 */
public final class SnapshotAllowance {
    /** Shared explanation of the supported SNAPSHOT subset, appended to rejection summaries. */
    public static final String SUPPORTED_SUBSET =
            "SNAPSHOT versions resolve only for workspace members and maven-local overlay artifacts already"
                    + " present on this machine; remote SNAPSHOT feeds are unsupported by design.";

    private static final SnapshotAllowance NONE = new SnapshotAllowance(Set.of(), List.of());

    private final Set<PackageId> workspaceMembers;
    private final List<RepositoryOverlay> overlays;
    private final MavenRepositoryPathBuilder repositoryPathBuilder = new MavenRepositoryPathBuilder();

    public SnapshotAllowance(Set<PackageId> workspaceMembers, List<RepositoryOverlay> overlays) {
        this.workspaceMembers = workspaceMembers == null ? Set.of() : Set.copyOf(workspaceMembers);
        this.overlays = overlays == null ? List.of() : List.copyOf(overlays);
    }

    /** An allowance that permits no SNAPSHOTs (no workspace, no overlays). */
    public static SnapshotAllowance none() {
        return NONE;
    }

    /** True when {@code version} is a SNAPSHOT that is backed by a workspace member or a local overlay artifact. */
    public boolean permitsSnapshot(PackageId packageId, String version) {
        if (!VersionPolicy.isSnapshot(version)) {
            return false;
        }
        return workspaceMembers.contains(packageId) || overlayContains(packageId, version);
    }

    /** True when a maven-local overlay is enabled for this resolution (used to shape rejection guidance). */
    public boolean overlaysEnabled() {
        return overlays.stream().anyMatch(overlay -> overlay.kind() == RepositoryOverlayKind.MAVEN_LOCAL);
    }

    /**
     * Actionable {@code Next:} guidance for a rejected SNAPSHOT. When the overlay is already enabled the
     * artifact just needs to be installed locally; otherwise it also names how to enable the overlay.
     */
    public String snapshotRemediation(String coordinate, String retryCommand) {
        String command = retryCommand == null || retryCommand.isBlank() ? "zolt resolve" : retryCommand.trim();
        if (overlaysEnabled()) {
            return "Install `" + coordinate
                    + "` into your local maven repository (~/.m2/repository) so the enabled maven-local overlay"
                    + " can supply it, then run `" + command + "` again.";
        }
        return "Install `" + coordinate
                + "` into your local maven repository (~/.m2/repository), then run `"
                + command + " --repository-overlay maven-local` again.";
    }

    private boolean overlayContains(PackageId packageId, String version) {
        if (overlays.isEmpty()) {
            return false;
        }
        ArtifactDescriptor jar = ArtifactDescriptor.jar(
                new Coordinate(packageId.groupId(), packageId.artifactId(), Optional.of(version)));
        String relativePath = repositoryPathBuilder.artifactPath(jar);
        for (RepositoryOverlay overlay : overlays) {
            if (overlay.kind() != RepositoryOverlayKind.MAVEN_LOCAL) {
                continue;
            }
            Path candidate = overlay.root().resolve(relativePath).normalize();
            if (Files.isRegularFile(candidate)) {
                return true;
            }
        }
        return false;
    }
}
