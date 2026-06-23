package com.zolt.build;

import java.util.List;
import java.util.Optional;

public record PackageMergeDecision(
        String kind,
        String path,
        Optional<String> target,
        List<String> sources) {
    public PackageMergeDecision {
        if (kind == null || kind.isBlank()) {
            throw new PackageException("Package merge decision kind is required.");
        }
        if (path == null || path.isBlank()) {
            throw new PackageException("Package merge decision path is required.");
        }
        target = target == null ? Optional.empty() : target;
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
