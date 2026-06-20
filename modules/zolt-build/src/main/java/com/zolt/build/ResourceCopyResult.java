package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

public record ResourceCopyResult(
        List<Path> copiedResources,
        List<Path> skippedResources) {
    public ResourceCopyResult(List<Path> copiedResources) {
        this(copiedResources, List.of());
    }

    public ResourceCopyResult {
        copiedResources = List.copyOf(copiedResources);
        skippedResources = List.copyOf(skippedResources);
    }

    public int copiedCount() {
        return copiedResources.size();
    }

    public int skippedCount() {
        return skippedResources.size();
    }

    public int resourceCount() {
        return copiedResources.size() + skippedResources.size();
    }
}
