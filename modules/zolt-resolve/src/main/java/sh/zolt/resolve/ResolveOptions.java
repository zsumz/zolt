package sh.zolt.resolve;

import sh.zolt.dependency.PackageId;
import sh.zolt.resolve.materialization.RepositoryOverlay;
import sh.zolt.resolve.progress.ArtifactProgressListener;
import java.util.List;
import java.util.Set;

public record ResolveOptions(
        boolean offline,
        List<RepositoryOverlay> repositoryOverlays,
        boolean rejectLocalOverlays,
        boolean includeCoverageTooling,
        String retryCommand,
        ArtifactProgressListener artifactProgressListener,
        Set<PackageId> workspaceMemberCoordinates) {
    public ResolveOptions {
        repositoryOverlays = repositoryOverlays == null ? List.of() : List.copyOf(repositoryOverlays);
        retryCommand = retryCommand == null || retryCommand.isBlank() ? "zolt resolve" : retryCommand.trim();
        artifactProgressListener = artifactProgressListener == null
                ? ArtifactProgressListener.NOOP
                : artifactProgressListener;
        workspaceMemberCoordinates =
                workspaceMemberCoordinates == null ? Set.of() : Set.copyOf(workspaceMemberCoordinates);
        if (rejectLocalOverlays && !repositoryOverlays.isEmpty()) {
            throw ResolveException.actionable(
                    "Cannot combine local repository overlays with local-overlay rejection.",
                    "Remove --repository-overlay or remove --no-local-overlays.");
        }
    }

    public ResolveOptions(
            boolean offline,
            List<RepositoryOverlay> repositoryOverlays,
            boolean rejectLocalOverlays,
            boolean includeCoverageTooling) {
        this(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                "zolt resolve",
                ArtifactProgressListener.NOOP,
                Set.of());
    }

    public ResolveOptions(boolean offline, List<RepositoryOverlay> repositoryOverlays, boolean rejectLocalOverlays) {
        this(offline, repositoryOverlays, rejectLocalOverlays, false);
    }

    public static ResolveOptions defaults() {
        return new ResolveOptions(false, List.of(), false);
    }

    public static ResolveOptions offline(boolean offline) {
        return new ResolveOptions(offline, List.of(), false);
    }

    public ResolveOptions withCoverageTooling() {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                true,
                retryCommand,
                artifactProgressListener,
                workspaceMemberCoordinates);
    }

    public ResolveOptions withRetryCommand(String retryCommand) {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                retryCommand,
                artifactProgressListener,
                workspaceMemberCoordinates);
    }

    public ResolveOptions withArtifactProgressListener(ArtifactProgressListener artifactProgressListener) {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                retryCommand,
                artifactProgressListener,
                workspaceMemberCoordinates);
    }

    public ResolveOptions withWorkspaceMemberCoordinates(Set<PackageId> workspaceMemberCoordinates) {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                retryCommand,
                artifactProgressListener,
                workspaceMemberCoordinates);
    }
}
