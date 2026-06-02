package com.zolt.build;

import com.zolt.resolve.ResolveResult;
import java.nio.file.Path;
import java.util.Optional;

public record BuildResult(
        Optional<ResolveResult> resolveResult,
        int sourceCount,
        Path outputDirectory,
        String compilerOutput) {
    public BuildResult {
        resolveResult = resolveResult == null ? Optional.empty() : resolveResult;
    }

    public boolean resolvedLockfile() {
        return resolveResult.isPresent();
    }
}
