package com.zolt.resolve;

import java.util.List;

public record ResolveOptions(
        boolean offline,
        List<RepositoryOverlay> repositoryOverlays,
        boolean rejectLocalOverlays) {
    public ResolveOptions {
        repositoryOverlays = repositoryOverlays == null ? List.of() : List.copyOf(repositoryOverlays);
        if (rejectLocalOverlays && !repositoryOverlays.isEmpty()) {
            throw new ResolveException(
                    "Cannot combine local repository overlays with local-overlay rejection. "
                            + "Remove --repository-overlay or remove --no-local-overlays.");
        }
    }

    public static ResolveOptions defaults() {
        return new ResolveOptions(false, List.of(), false);
    }

    public static ResolveOptions offline(boolean offline) {
        return new ResolveOptions(offline, List.of(), false);
    }
}
