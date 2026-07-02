package com.zolt.resolve;

import com.zolt.resolve.materialization.RepositoryOverlay;
import java.util.List;

public record ResolveOptions(
        boolean offline,
        List<RepositoryOverlay> repositoryOverlays,
        boolean rejectLocalOverlays,
        boolean includeCoverageTooling,
        String retryCommand) {
    public ResolveOptions {
        repositoryOverlays = repositoryOverlays == null ? List.of() : List.copyOf(repositoryOverlays);
        retryCommand = retryCommand == null || retryCommand.isBlank() ? "zolt resolve" : retryCommand.trim();
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
        this(offline, repositoryOverlays, rejectLocalOverlays, includeCoverageTooling, "zolt resolve");
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
        return new ResolveOptions(offline, repositoryOverlays, rejectLocalOverlays, true, retryCommand);
    }

    public ResolveOptions withRetryCommand(String retryCommand) {
        return new ResolveOptions(offline, repositoryOverlays, rejectLocalOverlays, includeCoverageTooling, retryCommand);
    }
}
