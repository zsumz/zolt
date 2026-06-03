package com.zolt.build;

import java.nio.file.Path;
import java.util.List;

public record ResourceCopyResult(List<Path> copiedResources) {
    public ResourceCopyResult {
        copiedResources = List.copyOf(copiedResources);
    }

    public int copiedCount() {
        return copiedResources.size();
    }
}
