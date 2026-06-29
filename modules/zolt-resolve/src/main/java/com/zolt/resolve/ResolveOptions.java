package com.zolt.resolve;

import com.zolt.resolve.materialization.RepositoryOverlay;
import java.util.List;

public record ResolveOptions(
        boolean offline,
        List<RepositoryOverlay> repositoryOverlays,
        boolean rejectLocalOverlays,
        boolean includeCoverageTooling) {
    public ResolveOptions {
        repositoryOverlays = repositoryOverlays == null ? List.of() : List.copyOf(repositoryOverlays);
        if (rejectLocalOverlays && !repositoryOverlays.isEmpty()) {
            throw new ResolveException(
                    "Cannot combine local repository overlays with local-overlay rejection. "
                            + "Remove --repository-overlay or remove --no-local-overlays.");
        }
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
        return new ResolveOptions(offline, repositoryOverlays, rejectLocalOverlays, true);
    }
}
