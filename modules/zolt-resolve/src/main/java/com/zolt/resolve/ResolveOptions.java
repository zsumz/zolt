package com.zolt.resolve;

import com.zolt.resolve.materialization.RepositoryOverlay;
import com.zolt.resolve.progress.ArtifactProgressListener;
import java.util.List;

public record ResolveOptions(
        boolean offline,
        List<RepositoryOverlay> repositoryOverlays,
        boolean rejectLocalOverlays,
        boolean includeCoverageTooling,
        String retryCommand,
        ArtifactProgressListener artifactProgressListener) {
    public ResolveOptions {
        repositoryOverlays = repositoryOverlays == null ? List.of() : List.copyOf(repositoryOverlays);
        retryCommand = retryCommand == null || retryCommand.isBlank() ? "zolt resolve" : retryCommand.trim();
        artifactProgressListener = artifactProgressListener == null
                ? ArtifactProgressListener.NOOP
                : artifactProgressListener;
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
                ArtifactProgressListener.NOOP);
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
                artifactProgressListener);
    }

    public ResolveOptions withRetryCommand(String retryCommand) {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                retryCommand,
                artifactProgressListener);
    }

    public ResolveOptions withArtifactProgressListener(ArtifactProgressListener artifactProgressListener) {
        return new ResolveOptions(
                offline,
                repositoryOverlays,
                rejectLocalOverlays,
                includeCoverageTooling,
                retryCommand,
                artifactProgressListener);
    }
}
